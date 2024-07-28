# ICU on Ravenwood

# What APIs are enabled
As of 2024-06-19, Ravenwood uses the host side JVM, not ART, so it doesn't use `libcore` either.

To support ICU on Ravenwood, we include the following jar files in the
Ravenwood classpath.
- `core-icu4j-for-host.ravenwood`
- `icu4j-icudata-jarjar`
- `icu4j-icutzdata-jarjar`

`core-icu4j-for-host.ravenwood` is made from `core-icu4j-for-host.ravenwood`
with `hoststubgen` to make the following modifications.
- Enable `android.icu` APIs on Ravenwood.
- But all other APIs -- i.e. all `libcore_bridge` APIS -- will throw at runtime.

This "policy" is defined in android_icu4j/icu-ravenwood-policies.txt.

As a result, on Ravenwood, all `android.icu` APIs will work, but none of the `libcore_bridge` APIs.

# CTS

ICU's CTS is `CtsIcuTestCases`, which contains the tests under
android_icu4j/src/main/tests/, which are the tests from the upstream ICU, and
android_icu4j/testing/, which are android specific tests, which depends
on `libcore_bridge`.

On Ravenwood, android_icu4j/src/main/tests/ will pass, but not android_icu4j/testing/.

So we have `CtsIcuTestCasesRavenwood-core-only`, which only contains the
tests from the upstream. You can run this with `atest CtsIcuTestCasesRavenwood-core-only`.
