# CHANGELOG

<!-- version list -->

## v0.4.0 (2026-11-26)

### Features

- **docs-site**: The site stamps the release it documents into its pages
  ([`3f604d0`](https://github.com/dmenezesgabriel/jupydroid/commit/3f604d0c552d85c9de477500ee126d719d23f0d4))

- **scripts**: The Markdown link gate reads HTML image embeds
  ([`16ab301`](https://github.com/dmenezesgabriel/jupydroid/commit/16ab30146a61d164ed765008c45fe8feff7f541b))

- **space**: A project environment is created from the app
  ([`97ea3f6`](https://github.com/dmenezesgabriel/jupydroid/commit/97ea3f63671ae8322a48e5ce15ff7d48f2d61284))

- **space**: A registered environment's kernel spec carries the env's libc
  ([`f298fcc`](https://github.com/dmenezesgabriel/jupydroid/commit/f298fcc193328846df3fe8cf3c60724fabd28643))

- **space**: An environment inside a project folder is named by its whole path
  ([`e828bd8`](https://github.com/dmenezesgabriel/jupydroid/commit/e828bd8110a461a6dd7d030b14dc11d9651cdd46))

- **space**: An existing interpreter or env registers as a kernel
  ([`09e059c`](https://github.com/dmenezesgabriel/jupydroid/commit/09e059c52eb43ac03c2df70f83308d05b30f0736))

- **space**: Environments inside a project's own folders are found
  ([`b4f539c`](https://github.com/dmenezesgabriel/jupydroid/commit/b4f539c400e878b887e1c65519d6b9994effcdf7))

- **ui**: The launcher draws a card per kernelspec
  ([`60486c8`](https://github.com/dmenezesgabriel/jupydroid/commit/60486c8702754d393b096482c32133920261cdae))

- **ui**: The Space's Python environments get a side panel
  ([`09dfcab`](https://github.com/dmenezesgabriel/jupydroid/commit/09dfcab663de76830d3d8f7276d42c4fafa41c8b))


## v0.3.1 (2026-11-24)

### Bug Fixes

- **space**: A passive read during a stop never starts the server back up
  ([`0e67dd1`](https://github.com/dmenezesgabriel/jupydroid/commit/0e67dd171696172d06cabe356cac95cbe8a096c5))

## v0.3.0 (2026-11-24)

### Bug Fixes

- **editor**: A refused interrupt folds into the run's own channel
  ([`fcb8876`](https://github.com/dmenezesgabriel/jupydroid/commit/fcb8876e19ae21e8268fd4ca9dc0f00b0de79d17))

- **editor**: A stop pressed while the kernel connects is kept, not dropped
  ([`62a0a1e`](https://github.com/dmenezesgabriel/jupydroid/commit/62a0a1e59b6b21be462ab7645442db458dff2431))

- **space**: Answer every suppressed seccomp SIGSYS with -ENOSYS at the tracer
  ([`8a8c0a2`](https://github.com/dmenezesgabriel/jupydroid/commit/8a8c0a27d9b1e88625200f52e5bd07c45fd4ff44))

### Features

- **editor**: A detached run keeps its own edges - writes, settings, file, key
  ([`c6c3130`](https://github.com/dmenezesgabriel/jupydroid/commit/c6c3130bd2be7ac2483d64e38151c556c7f151c7))

- **editor**: A run in flight is handed to the retained executions, not ended
  ([`b88723b`](https://github.com/dmenezesgabriel/jupydroid/commit/b88723b0ebce764a31facfae9e7bcd61314ac5f2))

- **editor**: Reopening adopts the retained run
  ([`f9fd784`](https://github.com/dmenezesgabriel/jupydroid/commit/f9fd784b73733df24e34b4f6235a367627f86c90))

## v0.2.0 (2026-11-22)

### Bug Fixes

- **ai**: A Space that cannot be asked fails the port, not the app
  ([`815e4b4`](https://github.com/dmenezesgabriel/jupydroid/commit/815e4b4794620208f3931f61dd04cd77bf5f4c8e))

- **ai**: A stalled Space server's chat-files read is a ChatFilesException
  ([`4987ee4`](https://github.com/dmenezesgabriel/jupydroid/commit/4987ee41390cc1495be5e380d9148a0a8db8bdd6))

- **app**: The installed app's label reads Jupydroid
  ([`8cf3e3d`](https://github.com/dmenezesgabriel/jupydroid/commit/8cf3e3d5814b5b43d2aa56b6407401b9ad4849c6))

- **audit**: Drag the tab strip to its start before the two-panes surface flow taps a tab
  ([`3cc9b9a`](https://github.com/dmenezesgabriel/jupydroid/commit/3cc9b9a8533acec41309cdb0b6547d9b8da9a317))

- **audit**: Hide the keyboard before a dialog flow dismisses the dialog before it
  ([`d14b1c0`](https://github.com/dmenezesgabriel/jupydroid/commit/d14b1c0d485290e08e53262d666ba7335a9eb052))

- **audit**: Scroll the tab strip to its start before the Save-before-close flow taps the file's tab
  ([`01b1405`](https://github.com/dmenezesgabriel/jupydroid/commit/01b1405f573dbe263383a7fc90772adfa02e27e1))

- **audit**: Start the tab-strip reveal from a tab that stays on screen
  ([`a54da95`](https://github.com/dmenezesgabriel/jupydroid/commit/a54da95c45f1cc497418d72e81d5bf279bab5dd2))

- **audit**: Wait for Source Control's line before the one-pane frame is taken
  ([`3cc104a`](https://github.com/dmenezesgabriel/jupydroid/commit/3cc104a72539e70b0600161e82e635d7162e93e3))

- **autopilot**: A session is told to wait on a result file or a pid, never pgrep -f on its own
  command
  ([`b243228`](https://github.com/dmenezesgabriel/jupydroid/commit/b243228fc3d3de87889d76655db8649c3bc8dc83))

- **chat**: Drop MCP servers under New chat when the two would squeeze its label
  ([`70b96fe`](https://github.com/dmenezesgabriel/jupydroid/commit/70b96fefa0cf9d612c0ae8a0191ebcc82bb33ec0))

- **editor**: A cell added from the footer is scrolled whole into view
  ([`8872622`](https://github.com/dmenezesgabriel/jupydroid/commit/8872622c43d5a3cd7c3a6d7c77a41fe5b2ee451e))

- **editor**: Draw a bottom sheet's status-bar icons for the app's theme
  ([`5f9011c`](https://github.com/dmenezesgabriel/jupydroid/commit/5f9011cb8be853fc504540f8a2100d29493cf19e))

- **editor**: Name the code field so a screen reader says what it edits
  ([`ff25248`](https://github.com/dmenezesgabriel/jupydroid/commit/ff25248db44ef65baa8a82dd589cbb64d13e946c))

- **explorer**: State a Files row's text colours so the open row stops reading 4.10:1
  ([`319dd2a`](https://github.com/dmenezesgabriel/jupydroid/commit/319dd2a06602ee3c81426b98b634edb01d2a741a))

- **fixtures**: The tour installs numpy in a cell and claims no more than it is
  ([`0f005c0`](https://github.com/dmenezesgabriel/jupydroid/commit/0f005c00558fd8ea1a9a6b1a44b7fb433ede7712))

- **fixtures**: The tour says the environment is on-device, not offline
  ([`670045b`](https://github.com/dmenezesgabriel/jupydroid/commit/670045bec1959f0d2ef76037685a07be801ffc91))

- **git**: A refused connection or a timeout reading git status is a GitStatusException, thrown by
  the adapter
  ([`c8a5688`](https://github.com/dmenezesgabriel/jupydroid/commit/c8a5688c58849e5816472fad9fcb37cf816d1dfc))

- **git**: A status read the server cannot answer is a GitStatusException
  ([`31a8988`](https://github.com/dmenezesgabriel/jupydroid/commit/31a8988cf921a15a1322f4c04a5848ae1c7f8237))

- **gradle**: Record the plugin portal's marker POM checksums strict verification lacked
  ([`d481063`](https://github.com/dmenezesgabriel/jupydroid/commit/d481063896df75baafa09f4e7aaa39e8b073bf8f))

- **launcher**: Collapse the empty cwd heading's margins into one
  ([`2dad03a`](https://github.com/dmenezesgabriel/jupydroid/commit/2dad03ab79c0be95a7bb469c5363a117725071b4))

- **launcher**: Grow a card to its label and its row at large font scales
  ([`8ddeacd`](https://github.com/dmenezesgabriel/jupydroid/commit/8ddeacdd0ccf9cc78e9f06f4eb7fb7591cc94028))

- **maestro**: Reset_notebooks clears the previous driver's .ipynb_checkpoints and keeps the
  kernel's HOME state
  ([`59bda94`](https://github.com/dmenezesgabriel/jupydroid/commit/59bda9450dadc119a9d8a089ad763b556af0ddf1))

- **maestro**: The tour's frames wait for the run to end before they scroll
  ([`69c3d6d`](https://github.com/dmenezesgabriel/jupydroid/commit/69c3d6decb9a04c9a60304b2354e228ad75d2b01))

- **maestro**: The tour's stats output sits below the fold, so the wait scrolls to it
  ([`dc368b1`](https://github.com/dmenezesgabriel/jupydroid/commit/dc368b153a4432b173e5e74cd0aff39f2e3fdb8a))

- **release**: The arm64 job verifies uv's aarch64 tarball (OPS-005 T5)
  ([`c2ce876`](https://github.com/dmenezesgabriel/jupydroid/commit/c2ce8764f613c7b6adafaef94e209368032d8f83))

- **rename**: Keep Cancel and Rename above the keyboard when the refusal grows the dialog
  ([`518800b`](https://github.com/dmenezesgabriel/jupydroid/commit/518800b03e8cd07ae5bc1dae9b88d1ddfe3f0b3b))

- **running**: Keep each row's close button at one x whatever the name's length
  ([`7df0dd7`](https://github.com/dmenezesgabriel/jupydroid/commit/7df0dd76fad9e36f5f508bac91b700dc3fad96d6))

- **running**: Reserve the accordion header's 48dp so it stops borrowing the toolbar button's
  ([`99fcdf7`](https://github.com/dmenezesgabriel/jupydroid/commit/99fcdf7b2f96550738e0b7d713cbba2cd618585b))

- **scripts**: A history rewrite started from a hook cannot reach the caller's repository
  ([`366acd9`](https://github.com/dmenezesgabriel/jupydroid/commit/366acd9496f86a87ce33b21a3f8664496234daf6))

- **scripts**: Reset a stale wm size override before the suite reads the window
  ([`ff309bf`](https://github.com/dmenezesgabriel/jupydroid/commit/ff309bf6ed1cbd2fd16e05255e394c0d7b44a19a))

- **scripts**: The autopilot loop sees a killed background task and survives a restart after stop
  ([`d7c22c4`](https://github.com/dmenezesgabriel/jupydroid/commit/d7c22c4a6fa7a5b67b00902abddce65b23f3ea43))

- **scripts**: The instrumented driver clears the ANR dialog that held window focus
  ([`0d94ee5`](https://github.com/dmenezesgabriel/jupydroid/commit/0d94ee588c19aebb17c8a83988e784408e403d8a))

- **settings**: Give the Theme row's segments 6dp of padding so a check sized from its label leaves
  Dark whole at 2.0
  ([`8d6bf4d`](https://github.com/dmenezesgabriel/jupydroid/commit/8d6bf4db792af5bd65bb1cf3d3ce2c7466aa8327))

- **settings**: Size each segment to its label so no word breaks at large font scales
  ([`0de2361`](https://github.com/dmenezesgabriel/jupydroid/commit/0de2361c98ad86a410a92abb45612176625fee8e))

- **shell**: Consume the status bar's inset once, for the document under the tab strip
  ([`143d524`](https://github.com/dmenezesgabriel/jupydroid/commit/143d524ea4e3e61fc332ced33b7d3d24b98cf295))

- **shell**: Scroll the tab strip to the active tab
  ([`3371c9d`](https://github.com/dmenezesgabriel/jupydroid/commit/3371c9dd1eb5be63eb134bc9c3aa7af5c7522c0f))

- **space**: A Space's interpreter adds its user site before the first install
  ([`55ae10d`](https://github.com/dmenezesgabriel/jupydroid/commit/55ae10d97af993377987835a6bea9619cad8db14))

- **space**: The install explanation reads the kernel's manylinux target
  ([`139400d`](https://github.com/dmenezesgabriel/jupydroid/commit/139400da60422298c79329ac05de8b70ee3cd7ce))

- **spaces**: Drop a row's controls under its name when they would squeeze the status word
  ([`e9ae194`](https://github.com/dmenezesgabriel/jupydroid/commit/e9ae194b1a37bfaadc70a9d71bbe15706ddacb37))

- **tabs**: A minimum-width tab's middle clears its close's reach, so a tap activates it and a long
  press opens its menu
  ([`28eb36c`](https://github.com/dmenezesgabriel/jupydroid/commit/28eb36c2d3563c7664e71216d7f96dd52e28f9c3))

- **tabs**: A tab is never wider than the row the one-pane strip leaves it, so the active tab's
  close is whole
  ([`95eaaf6`](https://github.com/dmenezesgabriel/jupydroid/commit/95eaaf6e3ae5b1cc05b7593e89f822510c6ccfef))

- **terminal**: Keep Ctrl and Alt apart like the other extra keys at large font scales
  ([`5d64848`](https://github.com/dmenezesgabriel/jupydroid/commit/5d648480de3874208ee0114cede609b3296cfe29))

- **terminal**: Name the terminal's input so a screen reader says what it types into
  ([`9a80a9b`](https://github.com/dmenezesgabriel/jupydroid/commit/9a80a9becbae59fb1b9d7216c2155e3114f467d4))

- **ui**: Anchor a cell's popups inside the field's decorator
  ([`b6418fb`](https://github.com/dmenezesgabriel/jupydroid/commit/b6418fb05d449718d75889b4f5291b6247cc3755))

- **ui**: Count the terminal cell at the pitch a run of glyphs steps
  ([`6471c96`](https://github.com/dmenezesgabriel/jupydroid/commit/6471c96f030d59547b36a22dbd0d2b5357798a47))

- **ui**: Count, draw and map a terminal cell at the font's fractional advance
  ([`e52a30b`](https://github.com/dmenezesgabriel/jupydroid/commit/e52a30b6d212313b606b8df6b2f1df892091d42b))

- **ui**: Draw a side panel's unstated text in sideBar.foreground
  ([`5dc9ca0`](https://github.com/dmenezesgabriel/jupydroid/commit/5dc9ca008a5110f2e58210b606378ce2cbf6e5f9))

### Features

- **ai**: A chat harness's catalog entry and Space probe follow the persona's own rules (JD-083 T1)
  ([`8b7a7c6`](https://github.com/dmenezesgabriel/jupydroid/commit/8b7a7c6f8d9d0eb3c31a615ea958b91b46c2372d))

- **ai**: A harness's install script ships byte-for-byte behind HarnessScripts (JD-083 T2)
  ([`062ca3e`](https://github.com/dmenezesgabriel/jupydroid/commit/062ca3ee13cbd79a1610943a807945e251ff8b37))

- **ai**: Draw the focus ring on the chat panel's buttons
  ([`2ba29f9`](https://github.com/dmenezesgabriel/jupydroid/commit/2ba29f98297d7949c67b87c8b712643162a46af0))

- **ai**: Size the chat panel's glyphs from their labels so they keep their ratio at large font
  scales
  ([`09fc84d`](https://github.com/dmenezesgabriel/jupydroid/commit/09fc84db1a1d8a62ecde7a0f62e4af145c79e3fd))

- **app**: PackageManagerAppIconRepository flips the aliases behind the port
  ([`b819f32`](https://github.com/dmenezesgabriel/jupydroid/commit/b819f32f52d6fc85f3066823748cc4f524f72cb2))

- **app**: Settings picks the launcher icon, previewing both designs
  ([`714fa97`](https://github.com/dmenezesgabriel/jupydroid/commit/714fa975adf17abe17717892017d53e8134466de))

- **app**: The app wears its own launcher icon
  ([`75f110d`](https://github.com/dmenezesgabriel/jupydroid/commit/75f110de4b84e0b2e0313ef23554b5ad0084e01c))

- **app**: The icon choice is a port - AppIcon observed and selected
  ([`37b1b6e`](https://github.com/dmenezesgabriel/jupydroid/commit/37b1b6e96831651de176ccf7b8b0430797974c19))

- **audit**: Capture the tablet at a chosen font scale and compare the two scales surface by surface
  ([`132b38a`](https://github.com/dmenezesgabriel/jupydroid/commit/132b38a75e3ec3573655bf4c4e93ab57a668f092))

- **audit**: Classify the null content descriptions and name text fields by what is beneath them
  ([`1ac0ddc`](https://github.com/dmenezesgabriel/jupydroid/commit/1ac0ddc76cb18e72a33d36af9c7e82547bb9fb3f))

- **audit**: Measure AUD-014's surface and open its ledger section
  ([`9e9c607`](https://github.com/dmenezesgabriel/jupydroid/commit/9e9c60722a58aef8797e7aed85ad7c268af324d0))

- **audit**: Name a disabled control apart in the text-contrast reader
  ([`83e0128`](https://github.com/dmenezesgabriel/jupydroid/commit/83e01283e8a7c605eb66bcd6b181ff9605cc92fa))

- **audit**: Read insets, gaps and unused space on the phone
  ([`d98db3b`](https://github.com/dmenezesgabriel/jupydroid/commit/d98db3b085efc50576e377e7ff94f4b0f0910fff))

- **audit**: Read insets, gaps and unused space on the tablet at one pane and in the dialogs
  ([`63d63d9`](https://github.com/dmenezesgabriel/jupydroid/commit/63d63d90a12ecd9165ac29d6cf171b053d0d95c1))

- **audit**: Read insets, gaps and unused space on the tablet at two panes
  ([`fd2b09f`](https://github.com/dmenezesgabriel/jupydroid/commit/fd2b09fb77229d4b73707f775ac30169b15c520f))

- **audit**: Read per-node text contrast in all six theme combinations
  ([`f64899a`](https://github.com/dmenezesgabriel/jupydroid/commit/f64899a574fd34019a2ec25d4b8747c2860b8c92))

- **audit**: Read text contrast at one pane in all six theme combinations
  ([`0591edf`](https://github.com/dmenezesgabriel/jupydroid/commit/0591edf4110bc2bfb75f00c244449f17446a4578))

- **audit**: Read text contrast in the dialogs at one pane in all six theme combinations
  ([`41f4046`](https://github.com/dmenezesgabriel/jupydroid/commit/41f40462dca95b9da7705d64196409b286538ffc))

- **audit**: Read text contrast on the launcher, the side panels and the Spaces home
  ([`29b2a2f`](https://github.com/dmenezesgabriel/jupydroid/commit/29b2a2f501683db029e65cc89082f8b94b6869ba))

- **audit**: Read text contrast on the phone in all six theme combinations
  ([`77efc26`](https://github.com/dmenezesgabriel/jupydroid/commit/77efc266ba6682f8773298d56bd88c5fa9fb31f7))

- **audit**: Read the contrast of every text node off a frame
  ([`37da979`](https://github.com/dmenezesgabriel/jupydroid/commit/37da979f6a769beaeefdd5508d2e29c6f39a4cac))

- **audit**: Read touch targets and the names a screen reader hears
  ([`4604de2`](https://github.com/dmenezesgabriel/jupydroid/commit/4604de2ce2316d6ef611aeffdcdcacc37db0bffc))

- **audit**: Read what a larger font scale does to a layout from two dumps of one screen
  ([`e018a57`](https://github.com/dmenezesgabriel/jupydroid/commit/e018a57d66f1cefe7233e10b69f0fcc29df70610))

- **audit**: Read what Android's high-contrast text does to the app's text
  ([`84132ff`](https://github.com/dmenezesgabriel/jupydroid/commit/84132ff8e2d0908d1e95d175090d77c281307aa7))

- **autopilot**: The loop can drive devin as well as claude, with the model and effort chosen and
  switchable
  ([`ba4c71f`](https://github.com/dmenezesgabriel/jupydroid/commit/ba4c71fa0be1cea158d41598d720b792fb007ee2))

- **diagnostics**: An error log port writes one redacted JSON line per entry into a day's file under
  the external files directory
  ([`55dcca6`](https://github.com/dmenezesgabriel/jupydroid/commit/55dcca64f30aedd3348ff59cd0c7802a707d7e84))

- **diagnostics**: An uncaught exception appends one crash entry to the error log, then reaches the
  default handler
  ([`2caca1c`](https://github.com/dmenezesgabriel/jupydroid/commit/2caca1c9bbcb6794f626873cb848a3b85f776245))

- **diagnostics**: At every start the platform's exit reasons since the last start are appended to
  the error log
  ([`b111d57`](https://github.com/dmenezesgabriel/jupydroid/commit/b111d572c411b99c7e4d5bb23911b1e0f6922431))

- **diagnostics**: Pull-app-logs.sh pulls the error log without run-as and read_app_log.py groups
  its entries by exception class and top frame
  ([`ecd06d5`](https://github.com/dmenezesgabriel/jupydroid/commit/ecd06d52ab074535cb60e87c9ce79c7d7efff603))

- **diagnostics**: Settings shows the error log's entries and size and clears it behind a
  confirmation that states the size
  ([`c715bd5`](https://github.com/dmenezesgabriel/jupydroid/commit/c715bd59215c969dc1b95220a054168b0a4a2d50))

- **diagnostics**: Settings' Diagnostics section offers "Share log", which hands every day's entries
  to the chooser as one file
  ([`e26a4e7`](https://github.com/dmenezesgabriel/jupydroid/commit/e26a4e7c8734b8f0c9fe21bbb0d3d355dd19636d))

- **diagnostics**: The error log prunes days past its age cap and the oldest past its size cap, at
  start and when a day's file opens
  ([`e1bed81`](https://github.com/dmenezesgabriel/jupydroid/commit/e1bed8178483782a8b0cd86c07ea1dfb0e313370))

- **editor**: A notebook ends in "Code" and "Markdown" buttons that each report the kind to append
  ([`138979f`](https://github.com/dmenezesgabriel/jupydroid/commit/138979f0295f799ff5aa95f274620bfd4a2332e0))

- **editor**: A package this Space's platform cannot have says why in the cell's output
  ([`fb027db`](https://github.com/dmenezesgabriel/jupydroid/commit/fb027db447264d92d0c2b7f231a3c3c7c013ae2c))

- **editor**: AppendCell adds an empty cell of a kind after the last one, active, in one undo step
  ([`b9ff564`](https://github.com/dmenezesgabriel/jupydroid/commit/b9ff56454a3269e484c86900320b6b815a9afbd9))

- **extension-api**: Draw the focus ring on the dialogs every extension asks the host for
  ([`5e719d4`](https://github.com/dmenezesgabriel/jupydroid/commit/5e719d4ac406f7924af8c64b6808eab28c291d11))

- **extension-api**: Keep a 24dp margin beside Delete and Rename's dialogs
  ([`8e38a32`](https://github.com/dmenezesgabriel/jupydroid/commit/8e38a327369e113cb191d285f1fd30bf800d1bb7))

- **fixtures**: The tour installs, computes, plots and renders a table
  ([`3069ca9`](https://github.com/dmenezesgabriel/jupydroid/commit/3069ca950334ad923db1f0673ccef7d0538ea36e))

- **git**: The Source Control panel says it is reading, and a failed read or no repository offers
  "Retry"
  ([`83fac05`](https://github.com/dmenezesgabriel/jupydroid/commit/83fac05b002d63752058417fb0cacd48dc152c47))

- **release**: Install the signed v0.1.1 release APK on the tablet and run a cell (JD-066 T6)
  ([`9c61207`](https://github.com/dmenezesgabriel/jupydroid/commit/9c61207573f2ae626b3a77798793b4a8be6fe95f))

- **scripts**: Check that site images ship in both colour schemes (OPS-010 T1)
  ([`8d56a10`](https://github.com/dmenezesgabriel/jupydroid/commit/8d56a10361e29d2662ad7e779316d481ee5554fa))

- **scripts**: The site screenshot driver also produces the getting-started frames (OPS-011 T2)
  ([`9e0f7b0`](https://github.com/dmenezesgabriel/jupydroid/commit/9e0f7b036fb98f056b7be5e3af90cb0e7a27072c))

- **space**: A bounded script runs in a Space's shell, its output streamed (JD-083 T3)
  ([`a639f65`](https://github.com/dmenezesgabriel/jupydroid/commit/a639f650ddd4050e6ac47725a14e53302993b844))

- **space**: A Space's server log rotates into a bounded archive on every start, so a restart never
  loses it
  ([`f0ae490`](https://github.com/dmenezesgabriel/jupydroid/commit/f0ae490b4c6103888f2aac4bc2c8b1fabb90938c))

- **space**: Every Space gains the gnu interpreter payload additively on start
  ([`27e7aec`](https://github.com/dmenezesgabriel/jupydroid/commit/27e7aecb580f3417eb564a44e150a7396b7c4600))

- **space**: The harnesses ViewModel probes on open and streams each install (JD-083 T5)
  ([`7b631dd`](https://github.com/dmenezesgabriel/jupydroid/commit/7b631ddf6b6ce542bd2398a2f5812dfcaaafcc7a))

- **space**: The Settings harnesses section probes, expands and installs (JD-083 T6)
  ([`b52fd2d`](https://github.com/dmenezesgabriel/jupydroid/commit/b52fd2d7add15828c30287b062d9db2acd88db55))

- **space**: The shipped proot is the patched seccomp build
  ([`8c64553`](https://github.com/dmenezesgabriel/jupydroid/commit/8c645532f870b09d474dba2e54408daba2b2dbd9))

- **space**: The Space's kernel runs on the gnu glibc interpreter
  ([`524d087`](https://github.com/dmenezesgabriel/jupydroid/commit/524d0878834ed9e823fc2b4b64b9c3da6d27c208))

- **theme**: Draw a 3:1 ring on a control that takes keyboard focus
  ([`48c3ad4`](https://github.com/dmenezesgabriel/jupydroid/commit/48c3ad4875beacb93ac531a4f110798e4f506b1e))

- **theme**: Draw the focus ring on a cell's selection tick
  ([`da75224`](https://github.com/dmenezesgabriel/jupydroid/commit/da7522432bb6142a7a02a480f949612c5d8f9960))

- **theme**: Draw the focus ring on every icon button
  ([`924e549`](https://github.com/dmenezesgabriel/jupydroid/commit/924e549e5bf5a7d1fd5749f3aa01b4eb31e5459c))

- **theme**: Draw the focus ring on every menu item and the More rail
  ([`a66e606`](https://github.com/dmenezesgabriel/jupydroid/commit/a66e60691287d24e6b27fc9e303c9778dc10665e))

- **theme**: Draw the focus ring on every text button and the launcher card
  ([`641292a`](https://github.com/dmenezesgabriel/jupydroid/commit/641292ac0ac857eb431b385f74b278e98a44347e))

- **theme**: Draw the focus ring on the rail's items
  ([`0af0a5e`](https://github.com/dmenezesgabriel/jupydroid/commit/0af0a5e7ae274478ac5910435d4b61671a39d6da))

- **theme**: Keep a 24dp margin beside a dialog's surface so wrapped text does not fill a phone
  ([`e428d53`](https://github.com/dmenezesgabriel/jupydroid/commit/e428d53a486c98e5867c8f414e677138a88a49fd))

- **theme**: Size a list row's glyph from its label so it keeps its ratio at large font scales
  ([`08b767d`](https://github.com/dmenezesgabriel/jupydroid/commit/08b767d3acc4cb58c85fd0c3a274bd436e63a414))

- **theme**: Size a tab's glyph from its label so it keeps its ratio at large font scales
  ([`f4f5562`](https://github.com/dmenezesgabriel/jupydroid/commit/f4f5562a7cab6117f4a95b2d6ccc35b7ee0b4b69))

- **theme**: Size More's rail glyph from its label so it keeps its ratio at large font scales
  ([`ea7cdc9`](https://github.com/dmenezesgabriel/jupydroid/commit/ea7cdc92133c2e4dabbf486fce814b60a62606de))

- **theme**: Size the editor rows' glyphs from their labels so they keep their ratio at large font
  scales
  ([`93aaa8e`](https://github.com/dmenezesgabriel/jupydroid/commit/93aaa8e9da5caebeb6a0439811cb88d2c0aeeb15))

- **theme**: Size the Material buttons' glyphs from their labels so they keep their ratio at large
  font scales
  ([`c9061e8`](https://github.com/dmenezesgabriel/jupydroid/commit/c9061e8e050deb6a8ea70f6e1f8249eb034ffa29))

- **theme**: Size the Running panel's glyphs from their labels so they keep their ratio at large
  font scales
  ([`1b5980f`](https://github.com/dmenezesgabriel/jupydroid/commit/1b5980fb2e813dae5f4d0a23de6dafacf891fc01))

- **ui**: Keep a 24dp margin beside every dialog's surface and ban the raw AlertDialog in ui
  ([`6bf990c`](https://github.com/dmenezesgabriel/jupydroid/commit/6bf990cb656df83cd20bc15a4af431a3ad77923e))

- **ui**: Leave a Space for the Spaces list from the one-pane tab strip
  ([`7df2c58`](https://github.com/dmenezesgabriel/jupydroid/commit/7df2c58e025903c67095f7dff5c2237ca9ef934e))

- **ui**: Leave a Space for the Spaces list from the rail at both widths
  ([`bcc3794`](https://github.com/dmenezesgabriel/jupydroid/commit/bcc3794458235f35c3396469e9eaa7423ee231be))

- **ui**: Read a finding from its squiggle and leave the gutter to the breakpoint
  ([`c61fee5`](https://github.com/dmenezesgabriel/jupydroid/commit/c61fee555b23108856c2f1058f0113676e507d48))

## v0.1.1 (2026-11-06)

### Bug Fixes

- **release**: Check that the tag is on main from the checkout, without a fetch (JD-066 T5)
  ([`d76e756`](https://github.com/dmenezesgabriel/jupydroid/commit/d76e756f938784b15d609390c7408a7a1856e75a))

- **release**: Tag a merged release pull request from the release commit it merged (JD-066 T2)
  ([`96b2351`](https://github.com/dmenezesgabriel/jupydroid/commit/96b2351daef56e765ded921995e23d7796c21cb0))

## v0.1.0 (2026-11-05)

- Initial Release
