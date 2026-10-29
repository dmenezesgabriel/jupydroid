// Floors under the build-only classpaths every module resolves - `ktlint` (the CLI the
// org.jlleitschuh plugin downloads) and `androidLintTool` (AGP's lint tool classpath).
// None of these jars ships in the APK. A constraint only ever raises what a carrier
// declares, so a plugin bump past a floor needs no edit here; the buildscript `classpath`
// floors for the same names live in the root `build.gradle.kts`.
//
// logback-core's six flagged advisories (GHSA-25qh-j22f-pwp8, -6v67-2wr5-gvf4,
// -jhq6-gfmj-v8fx, -p47f-322f-whfh, -pr98-23f8-jwxv, -qqpg-mvqg-649v) close at 1.5.34;
// 1.5.38 is the newest patch of that line, and classic floors beside core because the
// pair releases together. bcprov's three (GHSA-9pwp-9qqc-pr26, -c3fc-8qff-9hwx,
// -qp49-qgx5-5m26) close at 1.85 and bcpkix's GHSA-wg6q-6289-32hp at 1.84, so the
// jdk18on trio takes the 1.85 line at its newest patch per artifact - bcprov publishes
// a 1.85.2 the other two never got.
dependencies {
    constraints {
        add("ktlint", "ch.qos.logback:logback-core:1.5.38").because(
            "https://github.com/advisories/GHSA-pr98-23f8-jwxv",
        )
        add("ktlint", "ch.qos.logback:logback-classic:1.5.38").because(
            "releases with logback-core; same flagged version",
        )
        add("androidLintTool", "com.google.guava:guava:33.7.2-jre").because(
            "https://github.com/advisories/GHSA-xxph-c9ww-hj94",
        )
        add("androidLintTool", "org.apache.commons:commons-lang3:3.18.0").because(
            "https://github.com/advisories/GHSA-j288-q9x7-2f5v",
        )
        add("androidLintTool", "org.apache.httpcomponents:httpclient:4.5.14").because(
            "https://github.com/advisories/GHSA-7r82-7xv7-xcpj",
        )
        add("androidLintTool", "org.bouncycastle:bcprov-jdk18on:1.85.2").because(
            "https://github.com/advisories/GHSA-9pwp-9qqc-pr26",
        )
        add("androidLintTool", "org.bouncycastle:bcpkix-jdk18on:1.85").because(
            "https://github.com/advisories/GHSA-wg6q-6289-32hp",
        )
        add("androidLintTool", "org.bouncycastle:bcutil-jdk18on:1.85").because(
            "jdk18on trio; floors with bcprov",
        )
    }
}
