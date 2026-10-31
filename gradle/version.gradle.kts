// Shared by the app and release tooling; never compute a version from the host date.
val upstreamVersion = providers.gradleProperty("conduit.upstreamVersion").get()
val upstreamBuild = providers.gradleProperty("conduit.upstreamBuild").get().toInt()
val androidRevision = providers.gradleProperty("conduit.androidRevision").get().toInt()
require(upstreamVersion.matches(Regex("\\d+\\.\\d+\\.\\d+"))) { "Invalid upstream version" }
require(upstreamBuild in 1..21_474_835) { "Upstream build exceeds Android versionCode range" }
require(androidRevision in 1..99) { "Android revision must be 1..99" }
extra["conduitVersionName"] = "$upstreamVersion-a$androidRevision"
extra["conduitVersionCode"] = upstreamBuild * 100 + androidRevision
