{
  description = "Android Studio Development Environment for Taler Android";

  inputs = {
    nixpkgs.url = "nixpkgs/release-26.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        # Composed SDK specifically covering Taler Android parameters (up to API 36)
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "13.0"; 
          platformToolsVersion = "35.0.2";
          buildToolsVersions = [ "34.0.0" "35.0.0" "36.0.0" "37.0.0" ];
          platformVersions = [ "34" "35" "36" "37" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis_playstore" ];
          abiVersions = [ "x86_64" "arm64-v8a" ];
          
          includeNDK = true;
          ndkVersions = [ "29.0.14206865" ];
        };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            pkgs.android-studio
            androidComposition.androidsdk
            pkgs.jdk21          # Taler builds utilize JDK 21
            pkgs.nodejs       # Required for fetching/processing wallet-core JS modules
            pkgs.git
            pkgs.steam-run
            pkgs.libxcb
            pkgs.libxcb-cursor
          ];

          shellHook = ''
            export ANDROID_HOME="${androidComposition.androidsdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk-bundle"
            export JAVA_HOME="${pkgs.jdk21.home}"

            export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
            export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath [ pkgs.xorg.libxcb pkgs.libxcb-cursor ]}:$LD_LIBRARY_PATH"
            # Auto-generate local.properties to enforce Nix paths over system ones
            echo "sdk.dir=$ANDROID_HOME" > local.properties
            echo "ndk.dir=$ANDROID_NDK_ROOT" >> local.properties

            echo "🤖 Taler Android Wallet Shell Active!"
            echo "Run './gradlew :wallet:assembleDebug' to build just the wallet."
          '';
        };
      }
    );
}
