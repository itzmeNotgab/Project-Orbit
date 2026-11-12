#include <cstdio>
#include <cstring>
#include <stdexcept>

#include "orbit/compat.hpp"
#include "orbit/hud_modules.hpp"

using namespace orbit;
static int failures = 0, checks = 0;
#define CHECK(c) do { ++checks; if (!(c)) { ++failures; std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #c); } } while (0)

struct Boom : Module {
    Boom() : Module("boom", "Boom", "throws", Category::Utility) {}
    void onEnable() override { listen<int>([](const int&) {}); throw std::runtime_error("nope"); }
};
struct Plain : Module {
    int seen = 0;
    Plain() : Module("plain", "Plain", "counts ints", Category::Utility) { add(Setting::number("N", 5, 0, 10)); add(Setting::choice("C", {"a", "b"}, "a")); add(Setting::boolean("B", false)); add(Setting::color("Col", 0xFF112233)); }
    void onEnable() override { listen<int>([this](const int&) { ++seen; }); }
};

static std::set<std::string> all() { return {"render.frame", "player.tick", "world.time", "input.key", "net.ping"}; }

static void testBus() {
    EventBus bus; int a = 0, errs = 0;
    bus.setErrorHandler([&](const char*) { ++errs; });
    bus.subscribe<int>([](const int&) { throw std::runtime_error("x"); });
    auto t = bus.subscribe<int>([&](const int& v) { a += v; });
    bus.post(3); CHECK(a == 3); CHECK(errs == 1);
    bus.unsubscribe(t); bus.post(3); CHECK(a == 3);
    bus.post(std::string("no subscribers"));
    CHECK(bus.subscriberCount<int>() == 1);
}

static void testSettings() {
    auto f = Setting::number("f", 5, 0, 10);
    CHECK(f.decode("99") && f.getFloat() == 10); CHECK(!f.decode("abc")); CHECK(!f.decode("nan")); CHECK(!f.decode("")); CHECK(f.getFloat() == 10);
    f.setFloat(-5); CHECK(f.getFloat() == 0);
    auto c = Setting::choice("c", {"x", "y"}, "zzz"); CHECK(c.getChoice() == "x"); CHECK(!c.decode("q")); CHECK(c.decode("y"));
    auto col = Setting::color("col", 0xFF00FF00); CHECK(col.encode() == "#FF00FF00"); CHECK(!col.decode("#12")); CHECK(!col.decode("#GGGGGGGG")); CHECK(col.decode("#80010203") && col.getColor() == 0x80010203u);
    auto b = Setting::boolean("b", true); CHECK(!b.decode("yes")); b.decode("false"); CHECK(!b.getBool()); b.reset(); CHECK(b.getBool());
}

static void testJson() {
    std::map<std::string, std::string> m{{"a", "x\"y\\z\n"}, {"k.2", "v"}}, o;
    CHECK(json::read(json::write(m), o)); CHECK(o == m);
    CHECK(!json::read("{\"a\":", o)); CHECK(!json::read("[]", o)); CHECK(!json::read("{\"a\":\"b\"} junk", o)); CHECK(!json::read("{\"a\":1}", o));
    CHECK(json::read("{}", o) && o.empty());
}

static void testRegistry() {
    EventBus bus; ModuleRegistry r(bus); r.setGameInfo("1.26.60.4", all());
    auto p = std::make_unique<Plain>(); Plain* pp = p.get();
    CHECK(r.add(std::move(p))); CHECK(!r.add(std::make_unique<Plain>()));
    bus.post(1); CHECK(pp->seen == 0);
    CHECK(r.enable("plain")); bus.post(1); CHECK(pp->seen == 1);
    CHECK(r.disable("plain")); bus.post(1); CHECK(pp->seen == 1); CHECK(bus.subscriberCount<int>() == 0);
    CHECK(!r.enable("missing"));
    r.add(std::make_unique<Boom>());
    CHECK(!r.enable("boom")); CHECK(!r.find("boom")->enabled()); CHECK(r.find("boom")->lastError() == "nope"); CHECK(bus.subscriberCount<int>() == 0);
}

static void testUnsupported() {
    EventBus bus; ModuleRegistry r(bus); r.setGameInfo("1.26.60.4", {});
    modules::registerHud(r);
    for (auto* m : r.all()) { CHECK(!r.enable(m->id())); CHECK(!m->enabled()); CHECK(m->lastError().find("unsupported on 1.26.60.4") == 0); }
    r.setGameInfo("1.26.60.4", all());
    for (auto* m : r.all()) CHECK(r.enable(m->id()));
}

static void testPersistence() {
    std::string saved;
    {
        EventBus bus; ModuleRegistry r(bus); r.setGameInfo("v", all());
        r.add(std::make_unique<Plain>()); modules::registerHud(r);
        r.enable("plain"); r.enable("coords");
        auto* s = r.find("plain")->setting("N"); s->setFloat(8); r.find("plain")->setting("C")->setChoice("b");
        r.setFavorite("coords", true); saved = r.toJson();
    }
    EventBus bus; ModuleRegistry r(bus); r.setGameInfo("v", all());
    CHECK(r.fromJson(saved));
    r.add(std::make_unique<Plain>()); modules::registerHud(r);
    CHECK(r.find("plain")->enabled()); CHECK(r.find("coords")->enabled()); CHECK(!r.find("fps")->enabled());
    CHECK(r.find("plain")->setting("N")->getFloat() == 8); CHECK(r.find("plain")->setting("C")->getChoice() == "b"); CHECK(r.favorite("coords")); CHECK(!r.favorite("fps"));
    CHECK(!r.fromJson("not json")); CHECK(r.find("plain")->enabled());
    CHECK(r.fromJson("{\"mod.plain.N\":\"banana\",\"mod.plain.C\":\"zzz\"}")); CHECK(r.find("plain")->setting("N")->getFloat() == 8);
    // profiles
    CHECK(!r.saveProfile("../x")); CHECK(r.saveProfile("Match"));
    r.disable("plain"); r.find("plain")->setting("N")->setFloat(1);
    CHECK(r.loadProfile("Match")); CHECK(r.find("plain")->enabled()); CHECK(!r.loadProfile("Nope"));
    CHECK(r.profiles().size() == 1 && r.profiles()[0] == "Match");
}

static void testScanner() {
    std::uint8_t img[] = {0x00, 0xFD, 0x7B, 0x01, 0xA9, 0x10, 0x20, 0xFD, 0x7B, 0x02, 0xA9, 0x30};
    CHECK(SignatureScanner::parse("FD 7B ?? A9").size() == 4); CHECK(SignatureScanner::parse("FD ZZ").empty()); CHECK(SignatureScanner::parse("F").empty());
    auto r = resolveHooks(img, sizeof img, {{"unique", "10 20", 0}, {"amb", "FD 7B ?? A9", 0}, {"none", "AA BB", 0}, {"bad", "XX", 0}, {"off", "30", 2}});
    CHECK(r.resolved.count("unique") && r.resolved["unique"] == 5);
    CHECK(r.failures["amb"].find("ambiguous") != std::string::npos); CHECK(r.failures["none"] == "signature not found"); CHECK(r.failures["bad"] == "malformed signature");
    CHECK(r.resolved["off"] == 13);
    CHECK(r.names().count("unique") == 1);
    CHECK(SignatureScanner::find(img, 2, SignatureScanner::parse("FD 7B ?? A9")).empty());
}

static void testHud() {
    EventBus bus; ModuleRegistry r(bus); r.setGameInfo("v", all()); modules::registerHud(r);
    auto lines = [&](const char* id) { return r.find(id)->hudLines(); };
    for (auto* m : r.all()) r.enable(m->id());
    // FPS: 60 frames over 1 second
    for (int i = 0; i < 120; ++i) bus.post(events::FrameEvent{i / 60.0});
    CHECK(lines("fps").size() == 1 && lines("fps")[0] == "FPS: 60");
    r.find("fps")->setting("Show frame time")->setBool(true); CHECK(lines("fps")[0] == "FPS: 60 (16.7 ms)");
    // Coordinates
    CHECK(lines("coords").empty());
    bus.post(events::PlayerTickEvent{10.25, 64, -3.5, 90.f, 0.0});
    CHECK(lines("coords")[0] == "XYZ: 10.2, 64.0, -3.5" || lines("coords")[0] == "XYZ: 10.3, 64.0, -3.5");
    r.find("coords")->setting("Decimals")->setFloat(0); CHECK(lines("coords")[0] == "XYZ: 10, 64, -4" || lines("coords")[0] == "XYZ: 10, 64, -3");
    // Compass
    CHECK(lines("compass")[0] == "W");
    CHECK(modules::Compass::direction(0, false) == "S"); CHECK(modules::Compass::direction(180, false) == "N");
    CHECK(modules::Compass::direction(-90, false) == "E"); CHECK(modules::Compass::direction(270, true) == "East");
    CHECK(modules::Compass::direction(720 + 45, false) == "SW"); CHECK(modules::Compass::direction(359.9f, false) == "S");
    // Speed
    bus.post(events::PlayerTickEvent{0, 64, 0, 0, 100.0}); bus.post(events::PlayerTickEvent{4.3, 64, 0, 0, 101.0});
    CHECK(lines("speed")[0] == "Speed: 4.30 b/s");
    r.find("speed")->setting("Unit")->setChoice("km/h"); CHECK(lines("speed")[0] == "Speed: 15.48 km/h");
    bus.post(events::PlayerTickEvent{500, 64, 0, 0, 102.0}); CHECK(lines("speed")[0] == "Speed: 15.48 km/h");  // teleport ignored
    // World time
    CHECK(modules::WorldTime::format(0, false) == "06:00"); CHECK(modules::WorldTime::format(6000, false) == "12:00");
    CHECK(modules::WorldTime::format(18000, true) == "12:00 AM"); CHECK(modules::WorldTime::format(13500, false) == "19:30"); CHECK(modules::WorldTime::format(-1000, false) == "05:00");
    bus.post(events::WorldTimeEvent{6000}); CHECK(lines("worldtime")[0] == "Time: 12:00");
    // Keystrokes
    bus.post(events::KeyEvent{'W', true}); bus.post(events::KeyEvent{'X', true});
    CHECK(lines("keys")[0] == "  [W]" ); CHECK(lines("keys")[1] == " A  S  D ");
    bus.post(events::KeyEvent{'W', false}); CHECK(lines("keys")[0] == "   W ");
    // Ping
    bus.post(events::PingEvent{42}); CHECK(lines("ping")[0] == "Ping: 42 ms"); bus.post(events::PingEvent{-5}); CHECK(lines("ping").empty());
    // Disable stops reacting and clears state
    r.disable("ping"); bus.post(events::PingEvent{99}); CHECK(lines("ping").empty());
    r.disable("fps"); CHECK(lines("fps").empty()); bus.post(events::FrameEvent{500.0}); CHECK(lines("fps").empty());
}

int main() {
    testBus(); testSettings(); testJson(); testRegistry(); testUnsupported(); testPersistence(); testScanner(); testHud();
    std::printf("%d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
