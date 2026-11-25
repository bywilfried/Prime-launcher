# Lawnchair prebuilt libraries

Launcher3 has some dependencies on internal AOSP modules. 
To build Lawnchair, you have to build AOSP and obtain these JARs.

## Usage

Lawnchair relies on these JARs:

| File                       | Command                 | Android Tag                 | Target Configuration                               |
|----------------------------|-------------------------|-----------------------------|----------------------------------------------------|
| framework-16.jar           | `m framework`           | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| SystemUI-statsd-16.jar     | `m SystemUI-statsd`     | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| WindowManager-Shell-16.jar | `m WindowManager-Shell` | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| SystemUI-core-16.jar       | `m SystemUI-core`       | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| framework-15.jar           | `m framework`           | android-15.0.0_r3           |                                                    |
| framework-14.jar           | `m framework`           | android14-release           |                                                    |
| framework-13.jar           | `m framework`           |                             |                                                    |
| framework-12.jar           | `m framework`           | android12-platform-release  |                                                    |
| framework-11.jar           | `m framework`           | android-11.0.0_r18          |                                                    |
| framework-10.jar           | `m framework`           |                             |                                                    |

Location of the generated JARs:

| Module              | Path                                                                                                                             |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Framework           | ./soong/.intermediates/frameworks/base/framework/android_common/turbine-combined/framework.jar                                   |
| SystemUI-StatsD     | ./soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar         |
| WindowManager-Shell | ./soong/.intermediates/frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar |
| SystemUI-Core       | ./soong/.intermediates/frameworks/base/packages/SystemUI/SystemUI-core/android_common/javac/SystemUI-core.jar                    |

The 15 unused legacy JARs were removed in commit 24d40c0. Dev, Stable and F-Droid compilation succeeded in Actions run 45578003085.

## F-Droid review

The seven framework JARs (Android 10–16) and SystemUI-core-16 are compile-only inputs. SystemUI-statsd-16 and WindowManager-Shell-16 are also implementation dependencies. All ten Git blob identities match Lawnchair 16-dev at the time of the audit. See docs/FDROID-binary-audit.md.

The table above is inherited build documentation, not independent reproduction evidence. Android 10 and 13 source revisions and older target configurations remain unverified. Branch names for Android 12 and 14 must be resolved to immutable source revisions before claiming reproducibility. AOSP source: https://android.googlesource.com/platform/manifest . No scanner exceptions are applied; seek F-Droid reviewer guidance for these exact inputs.
