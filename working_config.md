# Compile Example Project

## Overview

This project serves as a minimal, known-good template for a Jetpack Compose application. Its configuration is based on a combination of other projects and has been debugged to a stable, buildable state.

Use this project as a reference or starting point if other projects fail to compile.

## Key Configuration Files

Below are the contents of the essential configuration files that make this project build successfully.

### `app/build.gradle`

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'org.jetbrains.kotlin.plugin.serialization' version '2.0.0'
}

android {
    namespace 'com.example.compile_example' // <-- Corrected namespace
    compileSdk 34

    defaultConfig {
        applicationId "com.example.compile_example" // <-- Corrected applicationId
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary true
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = '1.8'
    }
    packaging {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
        }
    }
    lint {
        disable "MutableCollectionMutableState"
        disable "AutoboxingStateCreation"
    }
}

dependencies {
    // Dependencies copied from alyf_observer
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.activity:activity-compose:1.9.0'
    implementation platform('androidx.compose:compose-bom:2023.08.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.material:material-icons-extended'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.2'
    // ... and so on
}
```

### `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TelegramAutomation">
        <activity
            android:name="com.example.compile_example.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

### `app/src/main/java/com/example/compile_example/MainActivity.kt`

```kotlin
package com.example.compile_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.compile_example.ui.theme.NewAndroidProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewAndroidProjectTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FloatingActionButton(onClick = { /* Do nothing for now */ }) {
                            Icon(Icons.Filled.Star, contentDescription = "Example Button")
                        }
                    }
                }
            }
        }
    }
}
```

## Troubleshooting Build Failures

This project's configuration was reached after solving several common build issues:

1.  **Missing `AndroidManifest.xml`**: The build will fail without this file. A minimal version (like the one above) is required.

2.  **`namespace` vs. `package`**: Modern Android Gradle Plugin requires the `namespace` to be defined in `app/build.gradle`. The `package` attribute should be removed from `AndroidManifest.xml` to avoid conflicts.

3.  **Lint `MissingClass` Error**: During the build, Lint may fail to resolve relative class names (e.g., `.MainActivity`) in the manifest, even if the configuration is correct. This can be due to caching.
    *   **Solution 1**: Run `./gradlew clean` to clear the build cache.
    *   **Solution 2 (More Robust)**: Use the fully-qualified class name in the manifest (`android:name="com.example.compile_example.MainActivity"`) to remove all ambiguity.

4.  **Theme/Typography Mismatch**: A `Theme.kt` file often depends on `Color.kt`, `Shapes.kt`, and `Type.kt`. If you copy a theme, ensure you copy all its dependent files and update their package names.

## Theme Configuration: Dynamic Color

This project's theme supports Android 12+ Dynamic Color, which adapts the app's colors to the user's wallpaper.

This is enabled by default in `Theme.kt`. You can control this feature from the call site in `MainActivity.kt`.

**To disable Dynamic Color** (and use the default purple theme):
```kotlin
NewAndroidProjectTheme(dynamicColor = false) {
    // ...
}
```

**To enable Dynamic Color**:
```kotlin
NewAndroidProjectTheme(dynamicColor = true) {
    // ...
}
```
