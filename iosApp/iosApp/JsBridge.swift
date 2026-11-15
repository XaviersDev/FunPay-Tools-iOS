import Foundation
import JavaScriptCore
import Shared

/// JavaScriptCore backend for FunPay Tools plugins (replaces Android's hidden WebView + @JavascriptInterface).
final class SwiftJsEngine: NSObject, JsEngineBackend {
    private let vm = JSVirtualMachine()
    private lazy var ctx: JSContext = JSContext(virtualMachine: vm)

    func install(handler: JsNativeHandler) {
        ctx.exceptionHandler = { _, exc in
            IosJsEngineKt.jsLog(message: "Ошибка: \(exc?.toString() ?? "?")")
        }
        let call: @convention(block) (String, JSValue) -> Any = { [weak self] name, argsValue in
            let args: [Any] = (argsValue.isArray ? (argsValue.toArray() ?? []) : [])
            let result = handler.call(name: name, args: args)
            guard let ctx = self?.ctx else { return NSNull() }
            switch result {
            case nil:
                return JSValue(undefinedIn: ctx) as Any
            case let b as KotlinBoolean:
                return JSValue(bool: b.boolValue, in: ctx) as Any
            case let s as String:
                return s
            case let n as NSNumber:
                return n
            default:
                return "\(result!)"
            }
        }
        ctx.setObject(call, forKeyedSubscript: "__fptCall" as NSString)
    }

    func evaluate(js: String) -> String? {
        guard let v = ctx.evaluateScript(js) else { return nil }
        if v.isUndefined || v.isNull { return "null" }
        if v.isBoolean { return v.toBool() ? "true" : "false" }
        if v.isNumber {
            let d = v.toDouble()
            if d == d.rounded(), abs(d) < 1e15 { return String(Int64(d)) }
            return String(d)
        }
        if v.isString {
            let s = v.toString() ?? ""
            if let data = try? JSONSerialization.data(withJSONObject: [s]), let arr = String(data: data, encoding: .utf8) {
                return String(arr.dropFirst().dropLast())
            }
            return "\"\(s)\""
        }
        if let json = ctx.objectForKeyedSubscript("JSON")?.invokeMethod("stringify", withArguments: [v]), json.isString {
            return json.toString()
        }
        return v.toString()
    }

    func close() {
        ctx.setObject(nil, forKeyedSubscript: "__fptCall" as NSString)
    }
}
