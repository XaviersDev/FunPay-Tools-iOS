import UIKit
import Shared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        MainViewControllerKt.appDidFinishLaunching()
        WidgetDataBridge.install()
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = RootViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func application(_ application: UIApplication,
                     supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return .allButUpsideDown
    }
}

/// Hosts the Compose UI and applies status bar / orientation requests coming from Kotlin.
final class RootViewController: UIViewController {
    private lazy var compose: UIViewController = MainViewControllerKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
        IosUiBridge.shared.onChange = { [weak self] in
            DispatchQueue.main.async { self?.applyBridge() }
            return KotlinUnit()
        }
    }

    private func applyBridge() {
        setNeedsStatusBarAppearanceUpdate()
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
            if let scene = view.window?.windowScene {
                scene.requestGeometryUpdate(.iOS(interfaceOrientations: supportedInterfaceOrientations)) { _ in }
            }
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

    override var childForStatusBarStyle: UIViewController? { nil }
    override var childForStatusBarHidden: UIViewController? { nil }
    override var childForHomeIndicatorAutoHidden: UIViewController? { nil }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        IosUiBridge.shared.statusBarDarkIcons ? .darkContent : .lightContent
    }

    override var prefersStatusBarHidden: Bool { IosUiBridge.shared.statusBarHidden }
    override var prefersHomeIndicatorAutoHidden: Bool { IosUiBridge.shared.statusBarHidden }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        switch IosUiBridge.shared.orientationMode {
        case 1: return .landscape
        case 2: return .allButUpsideDown
        default: return .portrait
        }
    }
}

/// Passes widget data to the App Group so a WidgetKit extension can show it.
enum WidgetDataBridge {
    static func install() {
        WidgetBridge.shared.publisher = { json in
            let defaults = UserDefaults(suiteName: "group.ru.allisighs.funpaytools") ?? UserDefaults.standard
            defaults.set(json, forKey: "widget_profile")
            return KotlinUnit()
        }
    }
}
