# Using CocoaPods

This sample demonstrates how to use a CocoaPods library from Kotlin/Native. It uses the the
[AFNetworking](https://cocoapods.org/pods/AFNetworking) library to retrieve a web-page by a
given URL.

## Configuring the project
1. [Install](https://guides.cocoapods.org/using/getting-started.html#installation) CocoaPods.
2. Navigate to the [kotlin-library](kotlin-library) directory and run
    ```
    ./gradlew podspec
    ```
   A [podspec](https://guides.cocoapods.org/syntax/podspec.html#specification) file for the
   Kotlin/Native library will be generated.
3. Navigate to the [ios-app](ios-app) directory and add a dependency on the generated podspec
   in the Podfile. Install the dependencies by running
    ```
    pod install
    ```
4. Open [ios-app.xcworkspace](ios-app/ios-app.xcworkspace) in Xcode and run the build.