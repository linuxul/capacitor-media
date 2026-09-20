import XCTest
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
}
