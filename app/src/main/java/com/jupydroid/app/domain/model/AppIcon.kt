package com.jupydroid.app.domain.model

/**
 * Which of the two designed launcher icons the app wears. Each value is one `activity-alias`
 * in `app/src/main/AndroidManifest.xml` - `.BlueOrbitIcon` or `.BlackOrbitIcon` - and naming
 * the artwork rather than the component keeps the manifest's mechanism out of the domain.
 */
enum class AppIcon {
  BLUE_ORBIT,
  BLACK_ORBIT,
}
