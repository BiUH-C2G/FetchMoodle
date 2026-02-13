# FetchMoodle

FetchMoodle: Kotlin Multiplatform library, fetch resources from your Moodle site

## Usage

For usage examples, please refer to our [Test](src/commonTest/kotlin/Test.kt) file

## Getting Started

1. Add Maven Repository
   Please add the following Maven repository address to your project:
   ```
   maven("https://earzuchan.github.io/Maven/") // Note: The 'M' in Maven must be capitalized
   ```
2. Introduce Dependencies
   Please introduce the following in your CommonMain:
   ```
   implementation("lib.fetchmoodle:core:<version>")
   ```
   For `<version>`, please refer to [Release](https://github.com/BiUH-C2G/FetchMoodle/releases)

## Test Credentials

1. Create a `local.properties` file in the root directory of the project
2. Fill in private values:
   - `moodle.test.url`
   - `moodle.test.username`
   - `moodle.test.password`

If these values are empty, integration login tests are fallbacked to the demo site