import XCTest
import Capacitor
@testable import MediaPlugin

class MediaTests: XCTestCase {
    func testPluginIsRegisteredUnderItsJavaScriptName() {
        let plugin = MediaPlugin()

        XCTAssertEqual(plugin.identifier, "MediaPlugin")
        XCTAssertEqual(plugin.jsName, "Media")
    }

    func testPluginExposesItsMethodsAsPromises() {
        let plugin = MediaPlugin()

        XCTAssertEqual(plugin.pluginMethods.map(\.name), [
            "getMedias",
            "getMediaByIdentifier",
            "getAlbums",
            "createAlbum",
            "savePhoto",
            "saveVideo",
            "getAlbumsPath"
        ])
        XCTAssertTrue(plugin.pluginMethods.allSatisfy { $0.returnType == .promise })
    }

    /// A call that fails the test if the method answers it itself: these methods answer by throwing, and the bridge
    /// rejects the call with the error.
    private func call(_ methodName: String, _ options: JSObject = [:]) -> CAPPluginCall {
        CAPPluginCall(callbackId: "test", methodName: methodName, options: options, success: { _, _ in
            XCTFail("\(methodName) must not resolve")
        }, error: { _ in
            XCTFail("\(methodName) answers by throwing")
        })
    }

    private func assertRejection(_ error: Error, _ message: String, code: String?, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual((error as? CAPPluginError)?.message, message, file: file, line: line)
        XCTAssertEqual((error as? CAPPluginError)?.code, code, file: file, line: line)
    }

    func testCreateAlbumWithoutANameIsRejectedBeforeAskingForAccess() async {
        do {
            try await MediaPlugin().createAlbum(call("createAlbum"))
            XCTFail("createAlbum must throw without a name")
        } catch {
            assertRejection(error, "Must provide a name", code: "argumentError")
        }
    }

    func testSavingWithoutAPathIsRejected() {
        XCTAssertThrowsError(try MediaPlugin().savePhoto(call("savePhoto"))) { error in
            assertRejection(error, "Must provide the data path", code: "argumentError")
        }
        XCTAssertThrowsError(try MediaPlugin().saveVideo(call("saveVideo"))) { error in
            assertRejection(error, "Must provide the data path", code: "argumentError")
        }
    }

    func testGetAlbumsPathIsUnimplementedOnIOS() {
        XCTAssertThrowsError(try MediaPlugin().getAlbumsPath(call("getAlbumsPath"))) { error in
            assertRejection(error, "Not implemented on iOS.", code: "UNIMPLEMENTED")
        }
    }
}
