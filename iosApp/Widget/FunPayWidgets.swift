import WidgetKit
import SwiftUI

enum AppGroup {
    static var id: String {
        if let groups = Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String], let first = groups.first {
            return first
        }
        return "group.ru.allisighs.funpaytools"
    }
    static var defaults: UserDefaults? { UserDefaults(suiteName: id) }
    static var container: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: id) }
}

struct ProfileData {
    var username = ""
    var balance = ""
    var rating = ""
    var reviews = ""
    var updated = ""
    var transparency = 0.0
    var bg = Color(red: 0.12, green: 0.12, blue: 0.14)
    var accent = Color(red: 0.24, green: 0.50, blue: 0.85)
    var text = Color.white
    var text2 = Color.gray
    var avatar: UIImage?
    var hasData: Bool { !username.isEmpty || !balance.isEmpty }

    static func load() -> ProfileData {
        var p = ProfileData()
        guard let json = AppGroup.defaults?.string(forKey: "widget_profile"),
              let data = json.data(using: .utf8),
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return p }
        p.username = o["username"] as? String ?? ""
        p.balance = o["balance"] as? String ?? ""
        p.rating = o["rating"] as? String ?? ""
        p.reviews = o["reviews"] as? String ?? ""
        p.updated = o["updated"] as? String ?? ""
        p.transparency = o["transparency"] as? Double ?? 0
        if let c = o["bg"] as? String { p.bg = Color(hex: c) ?? p.bg }
        if let c = o["accent"] as? String { p.accent = Color(hex: c) ?? p.accent }
        if let c = o["text"] as? String { p.text = Color(hex: c) ?? p.text }
        if let c = o["text2"] as? String { p.text2 = Color(hex: c) ?? p.text2 }
        if let url = AppGroup.container?.appendingPathComponent("widget_avatar.png"),
           let d = try? Data(contentsOf: url) { p.avatar = UIImage(data: d) }
        return p
    }
}

extension Color {
    init?(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        guard let v = UInt64(s, radix: 16) else { return nil }
        let a, r, g, b: Double
        if s.count == 8 {
            a = Double((v >> 24) & 0xff) / 255; r = Double((v >> 16) & 0xff) / 255
            g = Double((v >> 8) & 0xff) / 255; b = Double(v & 0xff) / 255
        } else if s.count == 6 {
            a = 1; r = Double((v >> 16) & 0xff) / 255; g = Double((v >> 8) & 0xff) / 255; b = Double(v & 0xff) / 255
        } else { return nil }
        self = Color(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

struct ProfileEntry: TimelineEntry {
    let date: Date
    let data: ProfileData
}

struct ProfileProvider: TimelineProvider {
    func placeholder(in context: Context) -> ProfileEntry {
        var d = ProfileData(); d.username = "FunPay"; d.balance = "0 ₽"; d.rating = "5.0"; d.reviews = "0"
        return ProfileEntry(date: Date(), data: d)
    }
    func getSnapshot(in context: Context, completion: @escaping (ProfileEntry) -> Void) {
        let d = ProfileData.load()
        completion(d.hasData ? ProfileEntry(date: Date(), data: d) : placeholder(in: context))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<ProfileEntry>) -> Void) {
        let entry = ProfileEntry(date: Date(), data: ProfileData.load())
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(15 * 60))))
    }
}

struct WidgetBackground: View {
    let data: ProfileData
    var body: some View {
        data.bg.opacity(max(0.0, 1.0 - data.transparency))
    }
}

extension View {
    @ViewBuilder
    func widgetBg(_ data: ProfileData) -> some View {
        if #available(iOS 17.0, *) {
            self.containerBackground(for: .widget) { WidgetBackground(data: data) }
        } else {
            self.background(WidgetBackground(data: data))
        }
    }
}

struct BalanceView: View {
    let entry: ProfileEntry
    var body: some View {
        let d = entry.data
        VStack(alignment: .leading, spacing: 6) {
            Text("FunPay").font(.system(size: 13, weight: .semibold)).foregroundColor(d.accent)
            Spacer(minLength: 0)
            Text(d.hasData ? (d.balance.isEmpty ? "0 ₽" : d.balance) : "Откройте приложение")
                .font(.system(size: d.hasData ? 24 : 14, weight: .bold))
                .foregroundColor(d.text)
                .minimumScaleFactor(0.5)
                .lineLimit(2)
            if !d.updated.isEmpty {
                Text(d.updated).font(.system(size: 10)).foregroundColor(d.text2)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(4)
        .widgetBg(d)
        .widgetURL(URL(string: "funpaytools://open"))
    }
}

struct ProfileView: View {
    let entry: ProfileEntry
    var body: some View {
        let d = entry.data
        HStack(spacing: 12) {
            Group {
                if let img = d.avatar {
                    Image(uiImage: img).resizable().scaledToFill()
                } else {
                    ZStack { d.accent.opacity(0.25); Image(systemName: "person.fill").foregroundColor(d.accent) }
                }
            }
            .frame(width: 56, height: 56)
            .clipShape(Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(d.hasData ? d.username : "FunPay Tools")
                    .font(.system(size: 16, weight: .bold)).foregroundColor(d.text).lineLimit(1)
                Text(d.hasData ? (d.balance.isEmpty ? "0 ₽" : d.balance) : "Откройте приложение")
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(d.accent).lineLimit(1)
                if d.hasData {
                    Text("★ \(d.rating.isEmpty ? "—" : d.rating) (\(d.reviews.isEmpty ? "0" : d.reviews))")
                        .font(.system(size: 12)).foregroundColor(d.text2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(4)
        .widgetBg(d)
        .widgetURL(URL(string: "funpaytools://open"))
    }
}

struct BalanceWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "FunPayBalance", provider: ProfileProvider()) { BalanceView(entry: $0) }
            .configurationDisplayName("Баланс FunPay")
            .description("Текущий баланс аккаунта")
            .supportedFamilies([.systemSmall])
    }
}

struct ProfileWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "FunPayProfile", provider: ProfileProvider()) { ProfileView(entry: $0) }
            .configurationDisplayName("Профиль FunPay")
            .description("Аватар, баланс и рейтинг")
            .supportedFamilies([.systemMedium])
    }
}

@main
struct FunPayWidgets: WidgetBundle {
    var body: some Widget {
        BalanceWidget()
        ProfileWidget()
    }
}
