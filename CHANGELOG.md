# CHANGELOG

<!-- version list -->

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

### Build System

- **rootfs**: Base on alpine 3.23 with zlib pinned to 1.4-r0 and scan the image we ship
  ([`e37b2be`](https://github.com/dmenezesgabriel/jupydroid/commit/e37b2beb524ed9c48d213218b526662bd2d88eae))

- **rootfs**: Move dnspython, opentelemetry-api, soupsieve and tornado to their fixed versions
  ([`522ffa7`](https://github.com/dmenezesgabriel/jupydroid/commit/522ffa7bda2c7f246ee4e6c90a965022efd9d610))

- **rootfs**: The gnu payload carries libgomp for the wheels that link OpenMP
  ([`12b6665`](https://github.com/dmenezesgabriel/jupydroid/commit/12b666569daf764abaf196b8be03bbf51e395040))

- **rootfs**: The gnu payload carries the X11/GL closure opencv-python links
  ([`b35da37`](https://github.com/dmenezesgabriel/jupydroid/commit/b35da37347ebdf8f72b0d87319dc4320e45216e1))

- **rootfs**: The gnu payload's runtime files come from debian:trixie-slim
  ([`bf7ae59`](https://github.com/dmenezesgabriel/jupydroid/commit/bf7ae59fa2f7168912a8465b006ed0c850d301d7))

- **scripts**: Let the doc-citations gate read TypeScript and treat JSON lines as data
  ([`55eee81`](https://github.com/dmenezesgabriel/jupydroid/commit/55eee819226a6a04b3a9fc2c3aa0441474433ff8))

- **scripts**: Run the Gradle hooks when a commit touches what Gradle reads, not on every commit
  ([`bf1fd29`](https://github.com/dmenezesgabriel/jupydroid/commit/bf1fd29b50a83c5e254a543b0f5fbafa404e25a1))

- **scripts**: Tell every autopilot session that a wait for the host is not a reason to end its turn
  ([`472645d`](https://github.com/dmenezesgabriel/jupydroid/commit/472645dba2efeff5afe7d1e53a08f1a33c15c3d2))

- **scripts**: The claude-autopilot loop drives prompt-execution sessions over acp
  ([`d5d38de`](https://github.com/dmenezesgabriel/jupydroid/commit/d5d38deb9c8f20abdcd897ea60f0e86abfcc9ac6))

- **scripts**: The loop's agent runs every demo itself with adb and Maestro and says no human did
  ([`c1ce4e2`](https://github.com/dmenezesgabriel/jupydroid/commit/c1ce4e257b6c253de874beea55de12fab9dcc486))

- **scripts**: Vendor the agent-harness skills at a pin and tell every autopilot session to use them
  ([`16db13a`](https://github.com/dmenezesgabriel/jupydroid/commit/16db13a00677b8e296580c0eb2b5dba4f3eb41be))

- **space**: Proot 5.4.0 rebuilt musl-static, dropping the SIGSYS for the loader's optional syscalls
  ([`105029f`](https://github.com/dmenezesgabriel/jupydroid/commit/105029f5840cb729eef50c168b53232466118d67))

- **space**: PROOT_SIGSYS_ENOSYS makes the tracer answer every seccomp SIGSYS with ENOSYS
  ([`a262bd8`](https://github.com/dmenezesgabriel/jupydroid/commit/a262bd8937b5405c4971f927a7094231964f0109))

- **space**: The gnu interpreter payload builds as its own asset beside the rootfs
  ([`946f72d`](https://github.com/dmenezesgabriel/jupydroid/commit/946f72d82360c5e3b3c0196eff283fa44922661d))

- **space**: The tracer's ENOSYS-safe set gains OpenBLAS's advisory NUMA calls
  ([`076c762`](https://github.com/dmenezesgabriel/jupydroid/commit/076c76216fb3b3aae5c1e28ef2072fb771b87ce8))

### Chores

- **repo**: Main required checks ruleset on (OPS-005 T3)
  ([`a0b5640`](https://github.com/dmenezesgabriel/jupydroid/commit/a0b56400cc7e1b2c4a65f9ebf936712b1d16a3bf))

- **repo**: Private vulnerability reporting on (OPS-005 T2)
  ([`28887e6`](https://github.com/dmenezesgabriel/jupydroid/commit/28887e6f7bd24dc0481ff0d0f0bdec56a9c83943))

- **repo**: Secret scanning and push protection on (OPS-005 T1)
  ([`5d64069`](https://github.com/dmenezesgabriel/jupydroid/commit/5d64069a5ad3ac1c35cbbef8a4c57d1607592eaa))

- **repo**: The repository's public settings are finished, and OPS-005 ships
  ([`d3cbae2`](https://github.com/dmenezesgabriel/jupydroid/commit/d3cbae29a65aa5da20a2b4b90c3c6433aae64b7b))

### Continuous Integration

- **dependabot**: Propose a newer Alpine base for the rootfs weekly
  ([`027d79b`](https://github.com/dmenezesgabriel/jupydroid/commit/027d79ba15f63d357eb657eed55e7138daed0de0))

- **docs**: The docs site's strict build runs as a commit hook on its inputs (OPS-009 T3)
  ([`258f26d`](https://github.com/dmenezesgabriel/jupydroid/commit/258f26d2a2c5c8d0d00e84bb69cbe6c92f6fd843))

- **github**: Build the docs site strict on every change and deploy it to Pages (OPS-009 T2)
  ([`058009a`](https://github.com/dmenezesgabriel/jupydroid/commit/058009a8a5e2821a1925aa9c77182ab0b4a1ed96))

- **probe**: An arm64 probe workflow proves the four rootfs builds natively (OPS-005 T5)
  ([`9be418a`](https://github.com/dmenezesgabriel/jupydroid/commit/9be418a61b908b35d722ca62e67e7e75defbdea2))

- **release**: The rootfs job builds on the native arm64 runner (OPS-005 T4)
  ([`70df768`](https://github.com/dmenezesgabriel/jupydroid/commit/70df768419adced6e1defc243ab1f2a7faf30226))

- **security**: The gitleaks scan allows the one fake token the error log's redaction tests use
  ([`3b24fa4`](https://github.com/dmenezesgabriel/jupydroid/commit/3b24fa4cbcd1c9918513f652cf77054f52115fca))

### Documentation

- **adr**: A cold dev36 boot's dialog is cleared, not prevented, because no setting gave none twice
  ([`027d153`](https://github.com/dmenezesgabriel/jupydroid/commit/027d1534aa87d82d00ec104fd9999d45efd0cacf))

- **adr**: A control carries its own name and a text field the name of what it edits
  ([`e9f9b70`](https://github.com/dmenezesgabriel/jupydroid/commit/e9f9b70c83367d9a3776d1b985064009a15feb2d))

- **adr**: A Space's kernel runs on a second, glibc interpreter beside Alpine's musl
  ([`415aae4`](https://github.com/dmenezesgabriel/jupydroid/commit/415aae411fe3c818b37f7a94b101762be78393bf))

- **adr**: A Space's server log rotates into its own archive under the error log's caps
  ([`76a09fb`](https://github.com/dmenezesgabriel/jupydroid/commit/76a09fb9e5e2cbeeaacf119d0e1ea43ca9a63b83))

- **adr**: Correct the cost of the maestro gate tests and say why they are not scoped
  ([`a2d3799`](https://github.com/dmenezesgabriel/jupydroid/commit/a2d37992558b5bab23eb52c852f0598b2ca7cc94))

- **adr**: Correct what skipping the Gradle hooks saves, and record what a commit still costs
  ([`95259cf`](https://github.com/dmenezesgabriel/jupydroid/commit/95259cfdf7483deab02585564b2b918189f9cc75))

- **adr**: Measure the dialog margin on both devices and record it
  ([`20b2796`](https://github.com/dmenezesgabriel/jupydroid/commit/20b2796eb52255222480d9579f9db8fb9bdba51a))

- **adr**: Record that a tab's floor keeps its middle clear of its close's reach, and ship JD-094
  ([`c6548c2`](https://github.com/dmenezesgabriel/jupydroid/commit/c6548c285268bbb7d96c96d8acbf32ba27270ff2))

- **adr**: Record that the instrumented suite is closed by shards on one build, and ship JD-092
  ([`64d193b`](https://github.com/dmenezesgabriel/jupydroid/commit/64d193bae04df5f795a9fef69d81cb828dc06951))

- **adr**: Record the keyboard focus ring's mark, colour and width
  ([`73576b3`](https://github.com/dmenezesgabriel/jupydroid/commit/73576b34c7e67a6ee5e5dd5c2fc85974c23600e7))

- **adr**: Record why a cell's popups emit inside the decorator, and ship JD-091
  ([`74f44f8`](https://github.com/dmenezesgabriel/jupydroid/commit/74f44f8e51b9290bd7b42fb48a0674110aab5017))

- **adr**: Record why a finding is read from its squiggle, and ship the item
  ([`2f734ee`](https://github.com/dmenezesgabriel/jupydroid/commit/2f734ee18d0207de069f2b6d414d48750a7b2b6e))

- **adr**: Record why a flow taps a field by its name, and ship JD-089 for the 20 taps it converted
  ([`4cec295`](https://github.com/dmenezesgabriel/jupydroid/commit/4cec295da4bb6ae52b92b63127638e5840d3b27a))

- **adr**: Record why an icon beside text follows the text's font size
  ([`dd7fca3`](https://github.com/dmenezesgabriel/jupydroid/commit/dd7fca3cf6e8276c96605024f564269980d39281))

- **adr**: Record why the bottom bar's centring is checked in pixels, and ship the item
  ([`1099cbc`](https://github.com/dmenezesgabriel/jupydroid/commit/1099cbc78ae08c0324e0f475e76793367fb0ccdc))

- **adr**: Record why the way back to the Spaces list is a named control, and ship the item
  ([`f4ca743`](https://github.com/dmenezesgabriel/jupydroid/commit/f4ca743bd786d058e0c0f39852a6eb0bd6345e41))

- **adr**: The list pane takes the library's width-class width
  ([`a0e1b0e`](https://github.com/dmenezesgabriel/jupydroid/commit/a0e1b0ec1afd8e7f5e7972295530faf1967a7c01))

- **adr**: The refusal mechanism splits by libc - glibc ENOSYS-only, musl keeps the handler
  ([`8235ff8`](https://github.com/dmenezesgabriel/jupydroid/commit/8235ff81c114557b67136aae844326f4e872d327))

- **adr**: The tab strip's controls keep the strip's height
  ([`3a96fd4`](https://github.com/dmenezesgabriel/jupydroid/commit/3a96fd451acb014419b4814827ef007e9230ed6f))

- **adr**: The terminal's margin is symmetric and the grid is counted inside it
  ([`c8c4cb6`](https://github.com/dmenezesgabriel/jupydroid/commit/c8c4cb62f33dac8c67f821d7af78ece06afc7493))

- **app**: A forced size reaches the window's measure since adaptive 1.3.0
  ([`c6a3ca8`](https://github.com/dmenezesgabriel/jupydroid/commit/c6a3ca817094976bcd203039a790e9a07b86c1b8))

- **arch**: Make the crash log automatic, bounded, clearable and readable on a release build
  ([`4e23afd`](https://github.com/dmenezesgabriel/jupydroid/commit/4e23afd48d70e7b1d0a5368de7ddc3c10bb857b0))

- **arch**: Record five usability gaps found on the published build
  ([`263a3b8`](https://github.com/dmenezesgabriel/jupydroid/commit/263a3b8e2f734992ca11a136b192ef3df1fc775a))

- **arch**: Record that no Chat harness can be installed from inside the app
  ([`e9d8910`](https://github.com/dmenezesgabriel/jupydroid/commit/e9d8910968733a0e56e1c35d204e4c923b7bd9fa))

- **arch**: Record the blank Source Control panel, an unexplained crash and the missing crash log
  ([`df411ad`](https://github.com/dmenezesgabriel/jupydroid/commit/df411adeeafd2a011c2778371e71dbab38763c68))

- **audit**: Close AUD-014 and name what it left
  ([`5b55d1b`](https://github.com/dmenezesgabriel/jupydroid/commit/5b55d1b79bc4d56d12c9ba69cd5a1fb64f6ec3ec))

- **audit**: Record 200% font scale on the phone
  ([`975f7db`](https://github.com/dmenezesgabriel/jupydroid/commit/975f7dbd18950b2ec2be192e6267ea6c94b92b17))

- **audit**: Record 200% font scale on the tablet at one pane and in the dialogs
  ([`52f8ee1`](https://github.com/dmenezesgabriel/jupydroid/commit/52f8ee1a9fe44cb50ec62e584f8bb4b55c226a75))

- **audit**: Record 200% font scale on the tablet at two panes and split the rest of the task
  ([`e749b9c`](https://github.com/dmenezesgabriel/jupydroid/commit/e749b9cd7b17a4b67200863dcc494a1fed9287f0))

- **audit**: Record borders and focus under high-contrast text, keyboard focus absent
  ([`eb82dd4`](https://github.com/dmenezesgabriel/jupydroid/commit/eb82dd43fb62bf45d7975151f86cfa4354ab1766))

- **audit**: Record Chat's MCP servers chip repaired at 200% font scale
  ([`94333f3`](https://github.com/dmenezesgabriel/jupydroid/commit/94333f3a1129bd994235d08c08d4e770758e45d6))

- **audit**: Record icons against text at 200% as absent beside IntelliJ
  ([`e6dfea3`](https://github.com/dmenezesgabriel/jupydroid/commit/e6dfea38ebe5235001b69c30ba322ecabcfc8823))

- **audit**: Record More's rail rows as library defaults
  ([`e52d1bd`](https://github.com/dmenezesgabriel/jupydroid/commit/e52d1bd901c0f4307636e8e2138136ae151df748))

- **audit**: Record Shut Down All's press area repaired
  ([`3262c24`](https://github.com/dmenezesgabriel/jupydroid/commit/3262c241470d539c04f8385cff5c2907c18fa56f))

- **audit**: Record the active tab revealed on the phone
  ([`e2e0efe`](https://github.com/dmenezesgabriel/jupydroid/commit/e2e0efe185d837f184001b353fd8917028ef9a6f))

- **audit**: Record the code cell and startup script named
  ([`06e98e0`](https://github.com/dmenezesgabriel/jupydroid/commit/06e98e0741bf16c4217959b525502515f1284b10))

- **audit**: Record the dark Files row's inherited colour repaired
  ([`7e5a8c1`](https://github.com/dmenezesgabriel/jupydroid/commit/7e5a8c13b944f6582c2c8d6334a73f7b388958ae))

- **audit**: Record the dialog's edge-to-edge width as the library's rule
  ([`2755fea`](https://github.com/dmenezesgabriel/jupydroid/commit/2755feaf69727421d5ddd6d2d3577d8df1ad0f33))

- **audit**: Record the five side panels' read in the six combinations and ship the side-bar
  foreground item
  ([`54bbee7`](https://github.com/dmenezesgabriel/jupydroid/commit/54bbee79034034d905466e0edeabadf3502ffded))

- **audit**: Record the launcher card read on the phone at 200% font scale
  ([`c5874e0`](https://github.com/dmenezesgabriel/jupydroid/commit/c5874e0ab47fbb3fdcf25018045348bbd12fd545))

- **audit**: Record the launcher card repaired on the tablet at 200% font scale
  ([`e18c35f`](https://github.com/dmenezesgabriel/jupydroid/commit/e18c35fe653d8f7dd316d5a3c6aa63e11ff00d62))

- **audit**: Record the launcher's blank and the bottom bar's inset
  ([`d4dd7f0`](https://github.com/dmenezesgabriel/jupydroid/commit/d4dd7f05f6a0822fa1f17ddac96250c6511ed39a))

- **audit**: Record the refused Rename repaired at 200% font scale
  ([`5c0512d`](https://github.com/dmenezesgabriel/jupydroid/commit/5c0512d01b4db430034a131dd1002aee81a98d06))

- **audit**: Record the Running panel's close column repaired at 200% font scale
  ([`9462f59`](https://github.com/dmenezesgabriel/jupydroid/commit/9462f59f1cade0018ce489ee6967468a72408ba0))

- **audit**: Record the Spaces home row repaired at 200% font scale
  ([`13a9cff`](https://github.com/dmenezesgabriel/jupydroid/commit/13a9cff01d2d7eac5cf1cc380605de2741e5fd74))

- **audit**: Record the status-bar icons under the table-of-contents sheet on the phone
  ([`c31031a`](https://github.com/dmenezesgabriel/jupydroid/commit/c31031a03877944644bd3543d266f12870a62b46))

- **audit**: Record the status-bar inset repair on the phone
  ([`50bef5d`](https://github.com/dmenezesgabriel/jupydroid/commit/50bef5d479bf2b5af8506466b113f2e62d1a84b4))

- **audit**: Record the terminal's Ctrl and Alt repaired at 200% font scale
  ([`98e4514`](https://github.com/dmenezesgabriel/jupydroid/commit/98e45147d1c5b885a83066229c4b2e79cb6ea27f))

- **audit**: Record the terminal's input named
  ([`48eb231`](https://github.com/dmenezesgabriel/jupydroid/commit/48eb2312ac62860423bf9cc0a4ab1293e74c4641))

- **audit**: Record the Theme row repaired at 200% font scale
  ([`1fa133e`](https://github.com/dmenezesgabriel/jupydroid/commit/1fa133e2d0d4488045373e1214a717070e919300))

- **audit**: Split the provisionals task into one task each
  ([`67892a0`](https://github.com/dmenezesgabriel/jupydroid/commit/67892a01f287d8812040062b2d4830982b8aa80e))

- **backlog**: Block the tablet-flow repair on the tablet and take the phone tab close target next
  ([`05b8593`](https://github.com/dmenezesgabriel/jupydroid/commit/05b859332f2e84934ce42fa80fec66069c48cc16))

- **backlog**: JD-092 is in-progress after T1's classification run
  ([`38ae191`](https://github.com/dmenezesgabriel/jupydroid/commit/38ae191095be6d32a8e22de91f5631f7dc2ef4d0))

- **backlog**: JD-096's mechanism must be the most native the evidence supports, not patch
  accumulation
  ([`74d760d`](https://github.com/dmenezesgabriel/jupydroid/commit/74d760dae88f39b3ad775f3ae2c7493e44598230))

- **backlog**: JD-096's must-list is thirty-six published packages, each proved by an official-docs
  snippet in the Space
  ([`bb57b90`](https://github.com/dmenezesgabriel/jupydroid/commit/bb57b908e0612fdc7cbb9b60e4afb204a3d2a1b0))

- **backlog**: OPS-005 is next
  ([`39028db`](https://github.com/dmenezesgabriel/jupydroid/commit/39028dba2c1fc09e5a636129c59e9d7ec112cc0a))

- **backlog**: OPS-013 cuts and verifies the next release end to end
  ([`deba833`](https://github.com/dmenezesgabriel/jupydroid/commit/deba833e0ab5fdd442b68bfcce87896503e0c4da))

- **backlog**: Published packages install as released, so JD-096 ships and GAP-196 closes
  ([`bad1c63`](https://github.com/dmenezesgabriel/jupydroid/commit/bad1c632ae95c76d5f6bfab113b9ce2ec537d496))

- **backlog**: Published packages installing outranks the explanation, so the next row is JD-096
  ([`631a291`](https://github.com/dmenezesgabriel/jupydroid/commit/631a291816c30c2c2090fdf4d836c49af5471190))

- **backlog**: Queue the instrumented suite's failures and tie the rootfs gap to what closed its kin
  ([`c2295f6`](https://github.com/dmenezesgabriel/jupydroid/commit/c2295f626d9a9ec55a0d20459c68e67aad3b5861))

- **backlog**: Record that a cold dev36 boot raises SystemUI's not-responding dialog, and open its
  cause
  ([`9f93577`](https://github.com/dmenezesgabriel/jupydroid/commit/9f93577e104eba19491551f8d5b63ede7498c558))

- **backlog**: Record that a finding's card covers its own line and queue the repair
  ([`eee494b`](https://github.com/dmenezesgabriel/jupydroid/commit/eee494b5f06a1aaf5ea72e1ab6b4d272afe2a002))

- **backlog**: Record that tablet flows tap an empty cell at a point that misses its field
  ([`506eee0`](https://github.com/dmenezesgabriel/jupydroid/commit/506eee0aaa85dd12f3ef0e66a396ebd071c09aa1))

- **backlog**: Record the off-centre bottom bar as a gap and queue its repair next
  ([`25c427b`](https://github.com/dmenezesgabriel/jupydroid/commit/25c427b6dee4820a616882a10c4fca5e2ddafab2))

- **backlog**: Record the rootfs's Critical zlib and vulnerable wheels as a gap and queue their
  repair
  ([`e8b03de`](https://github.com/dmenezesgabriel/jupydroid/commit/e8b03de0e68857f8f077883264abd63874919096))

- **backlog**: The app wears its own launcher icon before the release, JD-097 over GAP-212
  ([`64b0159`](https://github.com/dmenezesgabriel/jupydroid/commit/64b0159ec3ee110dac100a578d15437abb833a0a))

- **backlog**: The docs follow jupyterlab's user-guide shape and shots are produced in one pass
  ([`66fec2d`](https://github.com/dmenezesgabriel/jupydroid/commit/66fec2d841d96bf67238b9c96dd97cfb647727fe))

- **backlog**: The docs items census jupyterlab's and vscode's sites before writing
  ([`9bb7d56`](https://github.com/dmenezesgabriel/jupydroid/commit/9bb7d56e45b9c63281671a0a5c8e3cdc9e3aaf79))

- **backlog**: The error log keeps a Space's crashed server log too, so JD-082 ships and GAP-200
  closes
  ([`d36862c`](https://github.com/dmenezesgabriel/jupydroid/commit/d36862cb0021215980e45c1206685ccec61de09e))

- **backlog**: The explanation ships, so JD-078's row names T4 as what is left
  ([`7914b70`](https://github.com/dmenezesgabriel/jupydroid/commit/7914b702247043101d7e95f2e64d43f643a9811d))

- **backlog**: The icon's densities may be resampled with imagemagick
  ([`ac1da1e`](https://github.com/dmenezesgabriel/jupydroid/commit/ac1da1e6ec3ae9e18d497c751ac4e053e3779d19))

- **backlog**: The launcher icon is chosen in Settings, the blue orbit a fresh install's
  ([`85aa34d`](https://github.com/dmenezesgabriel/jupydroid/commit/85aa34dd64ea9c2aa5ac3ccfd940a9cafc9d7858))

- **backlog**: The tablet is attached again, so the next row is JD-093
  ([`d1c3632`](https://github.com/dmenezesgabriel/jupydroid/commit/d1c3632cde63faf67662e858e9c77451ab992e8f))

- **community**: The conduct contact points at GitHub's private reporting, and OPS-005 says what
  remains
  ([`9802464`](https://github.com/dmenezesgabriel/jupydroid/commit/98024643263aa8d341561b96dfe4453b081fa596))

- **diagnostics**: The error log's policy is an ADR: local, redacted when written, capped, shared
  only by the person
  ([`bd61dfb`](https://github.com/dmenezesgabriel/jupydroid/commit/bd61dfbd11342cf27b4c85d787b1b04ef9bf4db0))

- **maestro**: The text-output-link driver's Chrome step did not reproduce in five runs, so it stays
  as it is
  ([`47254ca`](https://github.com/dmenezesgabriel/jupydroid/commit/47254ca8d503daf9774f56052a6dd630cc19612a))

- **prd**: 71 of the 1000 most downloaded PyPI packages have no wheel a Space's pip can install,
  none of the first 100
  ([`757dd1d`](https://github.com/dmenezesgabriel/jupydroid/commit/757dd1d4230b24604448a783698d89a4faab2bbe))

- **prd**: A cold dev36 boot is counted per boot before any cause is changed, so write the item
  ([`74dee5e`](https://github.com/dmenezesgabriel/jupydroid/commit/74dee5e14f047456dd26f8e530ca7e5978c50351))

- **prd**: A minimum-width tab's middle is the close's, so write the item that moves the floor off
  it
  ([`b127956`](https://github.com/dmenezesgabriel/jupydroid/commit/b127956c7f1d5da987c04aa217da4d8860abd3d2))

- **prd**: A notebook has no empty or read-only state, so JD-079's footer drops two conditions the
  row named
  ([`7e634ff`](https://github.com/dmenezesgabriel/jupydroid/commit/7e634ffe295be77e946601514d1f86df2c8c26af))

- **prd**: A second 5 cold dev36 boots with -cores 2 gave the dialog in all 5, so the setting is not
  kept
  ([`9dd9404`](https://github.com/dmenezesgabriel/jupydroid/commit/9dd9404ae81ab6ebfd0e3284407fd5947c0ee294))

- **prd**: Attribute the bottom bar's off-centre to pixel rounding under ForcedSize
  ([`abc906b`](https://github.com/dmenezesgabriel/jupydroid/commit/abc906b5eeb063eff563b22a0cf6cdb4f72461f3))

- **prd**: Five cold dev36 boots gave the dialog in three, and the emulator raises the AVD's RAM to
  2560 MB itself
  ([`aab0076`](https://github.com/dmenezesgabriel/jupydroid/commit/aab007608997f2a15bd619a19de6f1a20acd462e))

- **prd**: Giving dev36 2 cores in place of 4 left no dialog in 5 cold boots, the first arm to give
  none
  ([`5c577b6`](https://github.com/dmenezesgabriel/jupydroid/commit/5c577b61ef2607f3ca43b400454d95f55119bdd7))

- **prd**: Giving dev36 4096 MB of RAM left the dialog in 3 of 5 cold boots, as the baseline did
  ([`b56c513`](https://github.com/dmenezesgabriel/jupydroid/commit/b56c513247f1feeb5eac474cede1d7c1947e2ed5))

- **prd**: JD-078's T2b, T3 and T4 wait for the tablet, so the next row is JD-079
  ([`ae9c62d`](https://github.com/dmenezesgabriel/jupydroid/commit/ae9c62d912c9e7dafb83929765c80e33a72611b2))

- **prd**: JD-080 says what the Source Control panel does in each state, and an IOException from the
  adapter is the cause of its blank one
  ([`d0a9eb6`](https://github.com/dmenezesgabriel/jupydroid/commit/d0a9eb6b4d767748292e268a7332d2d3247f2129))

- **prd**: JD-080's demo waits for the tablet and its plan is kept, so the next row is JD-082
  ([`09ab73b`](https://github.com/dmenezesgabriel/jupydroid/commit/09ab73b8e9f522d9174a70fb2349271269eb993e))

- **prd**: JD-081's plan is written - reproduce the crash on the tablet, name its cause from the
  error log, repair it red-first
  ([`a4e5826`](https://github.com/dmenezesgabriel/jupydroid/commit/a4e5826e5c01ee88b96ecdd47478bdcf611fc5b5))

- **prd**: JD-083's plan is written - a harness installs from a stopped Space's Settings through a
  catalog, a streaming SpaceShell port and a live probe
  ([`ee2ee1f`](https://github.com/dmenezesgabriel/jupydroid/commit/ee2ee1f39a8f34d37e90e6220214410fe0078043))

- **prd**: JD-096 is picked up, and its premise test is named before any candidate is built
  ([`333c7c0`](https://github.com/dmenezesgabriel/jupydroid/commit/333c7c0f40d3dcd3cc1c7eb1b026f2f16fc39f65))

- **prd**: JD-096's must-list is carried into the PRD, and each package is proved by its own
  quickstart
  ([`2b3f896`](https://github.com/dmenezesgabriel/jupydroid/commit/2b3f896523ff90b106e9017726c83a3a113aa3bd))

- **prd**: JD-097's plan is written - two designed icons behind activity-aliases, the choice in
  PackageManager's component state, picked in Settings
  ([`470dc98`](https://github.com/dmenezesgabriel/jupydroid/commit/470dc9800d4324610d5b6f6b4791e693a4cc1a85))

- **prd**: Measure the shared gutter cell on a diagnosed line and settle the finding's target
  ([`861aa00`](https://github.com/dmenezesgabriel/jupydroid/commit/861aa0018862fcba71488ee6e575b79d96613b4b))

- **prd**: Neither duckdb nor scikit-learn has a musllinux aarch64 wheel in any release, so write
  the item that chooses between explaining and bundling
  ([`bf631ba`](https://github.com/dmenezesgabriel/jupydroid/commit/bf631ba8ccc4e9b9a4d7df62cdf74090a4ce3aa1))

- **prd**: OPS-005's plan is written - the repository's public settings finished
  ([`bc597a7`](https://github.com/dmenezesgabriel/jupydroid/commit/bc597a74b3250fbdc6e09e01ef42846586ecfe13))

- **prd**: OPS-010's plan is written - the site's pictures produced by one tablet command, in light
  and dark
  ([`435b866`](https://github.com/dmenezesgabriel/jupydroid/commit/435b86694bcb62ee5de4cf46e6710dcd05f06631))

- **prd**: OPS-011's plan is written - install to the first notebook run
  ([`764c88f`](https://github.com/dmenezesgabriel/jupydroid/commit/764c88f78015a8b07dfeeb01536a43c8e4aad401))

- **prd**: OPS-012 walks the built site in a browser before it ships
  ([`423219d`](https://github.com/dmenezesgabriel/jupydroid/commit/423219ddbb1ae479cb9ab180b03fa3b3aa78a322))

- **prd**: OPS-012's plan is written - a page per feature area
  ([`342866e`](https://github.com/dmenezesgabriel/jupydroid/commit/342866eb481862acbeee72bf5bef938c8ef9289a))

- **prd**: OPS-020's plan is written - the next release cut and verified end to end
  ([`b8d2caa`](https://github.com/dmenezesgabriel/jupydroid/commit/b8d2caab52ef9a134ef09e6ee0637d0e22621629))

- **prd**: Record the red run and the code cell's node as read on the tablet
  ([`cbe7f4f`](https://github.com/dmenezesgabriel/jupydroid/commit/cbe7f4fb3335c9203c6911a246d5233d374e9326))

- **prd**: Restoring a clean dev36 snapshot gave no dialog in 5 boots, which says nothing about a
  cold boot
  ([`64c1653`](https://github.com/dmenezesgabriel/jupydroid/commit/64c1653427eaa85ebcd8c17d9895115cc4b07df1))

- **prd**: Settle where JD-075's way back to Spaces lives and what leaving does today
  ([`0de7d2a`](https://github.com/dmenezesgabriel/jupydroid/commit/0de7d2a1c0069e5095db3ee10f9a81e730dad3b3))

- **prd**: Shard S1 fails 4 of 121 tests where the whole run failed 17
  ([`25cee5f`](https://github.com/dmenezesgabriel/jupydroid/commit/25cee5f3c96444b8808179de0eacecac8b19f87b))

- **prd**: Shard S10 ran 349 tests and 1 failed, the search chevron test that failed in S1 and
  passed alone
  ([`a4f4c1b`](https://github.com/dmenezesgabriel/jupydroid/commit/a4f4c1bc22a6b6742ccb0b90a9d0288b500ba3df))

- **prd**: Shard S2 ran 61 tests and 1 failed, a test of a gutter mark that no longer exists
  ([`7f8afb4`](https://github.com/dmenezesgabriel/jupydroid/commit/7f8afb467c977d442ba2fd29aba1ec06cc9ebb5c))

- **prd**: Shard S3 ran 58 tests and 1 failed, a one-pane tab strip that leaves the active tab's
  last 16dp under Reload
  ([`074fadb`](https://github.com/dmenezesgabriel/jupydroid/commit/074fadb150b4a6751b3f3e1245b574e762ed4884))

- **prd**: Shard S5 ran 143 tests and none failed, the theme, explorer, file editor, spaces,
  settings and intent classes
  ([`09b8bd0`](https://github.com/dmenezesgabriel/jupydroid/commit/09b8bd012b1cb6d649d73f8b09e5966714767186))

- **prd**: Shard S6 ran 78 tests and none failed, the 22 data/space classes
  ([`ef0703e`](https://github.com/dmenezesgabriel/jupydroid/commit/ef0703e1bd683ce452c331caa88dd29c1f053197))

- **prd**: Shard S7 ran 177 tests and none failed, the 28 ui/shell classes no earlier shard ran
  ([`c8b453d`](https://github.com/dmenezesgabriel/jupydroid/commit/c8b453d50e1eadf2abff4707656a2dbf2669d8e9))

- **prd**: Shard S8 ran 286 tests and none failed, the 48 ui/editor classes no earlier shard ran
  ([`975d062`](https://github.com/dmenezesgabriel/jupydroid/commit/975d06240df7f032748e36b07587090a3bd11a63))

- **prd**: Shard S9 ran 184 tests and none failed, the 45 root and ui classes no earlier shard ran
  ([`1c5f2b9`](https://github.com/dmenezesgabriel/jupydroid/commit/1c5f2b9b71b1efe734fa221ba8a2950e7f150382))

- **prd**: Split AUD-014's close into seven tasks
  ([`c68fdca`](https://github.com/dmenezesgabriel/jupydroid/commit/c68fdca873975f73f84a80cd444a50dd29c959e9))

- **prd**: Split AUD-014's last task into borders and focus, the leftover rows and the close
  ([`e7807b8`](https://github.com/dmenezesgabriel/jupydroid/commit/e7807b8a0d9533109c200db7154795a6b465332e))

- **prd**: Split AUD-014's layout read into three tasks
  ([`7b70acf`](https://github.com/dmenezesgabriel/jupydroid/commit/7b70acf5e90290b9815e5171d3a3788d16e48c2c))

- **prd**: Split AUD-014's provisional rows into one task each
  ([`9059453`](https://github.com/dmenezesgabriel/jupydroid/commit/9059453fb5070adac4c8da9917ad0428ff9736da))

- **prd**: Split AUD-014's T7d into the pane width and the touch bounds
  ([`2f51fae`](https://github.com/dmenezesgabriel/jupydroid/commit/2f51faeb7e4b4f6be4fa596065ccf0c5f6911c05))

- **prd**: Split AUD-014's T7e into one task per font-scale defect
  ([`793cce0`](https://github.com/dmenezesgabriel/jupydroid/commit/793cce0bdd1b7747c6c75a07f5cd4bf9f198ffb9))

- **prd**: Split AUD-014's tablet half into four tasks
  ([`aa583df`](https://github.com/dmenezesgabriel/jupydroid/commit/aa583df9ede2a305bd9d0ac5cfb42032b12edb23))

- **prd**: Split AUD-014's targets, names and font scale into three tasks
  ([`126e9af`](https://github.com/dmenezesgabriel/jupydroid/commit/126e9af548d5e5229b0a9e8a8f01cdc33fb2f066))

- **prd**: Split JD-068's extension task in two
  ([`accf3c4`](https://github.com/dmenezesgabriel/jupydroid/commit/accf3c4a54b36574f63b76c06a6d066ea1f7f713))

- **prd**: Split JD-084's button-glyph task by module
  ([`12a7e25`](https://github.com/dmenezesgabriel/jupydroid/commit/12a7e252c44af0dd98537d529cf8ac165b40c39c))

- **prd**: Split JD-084's other-sites task by surface
  ([`77e93ef`](https://github.com/dmenezesgabriel/jupydroid/commit/77e93efed1ca0ca240a22b7c05bc90730e9ae39f))

- **prd**: Split the dialog margin's device read behind the two dialogs :extension-api owns
  ([`29ae272`](https://github.com/dmenezesgabriel/jupydroid/commit/29ae272956bff5e10148cbdb45dd77575cad4984))

- **prd**: Split the flows that stop after their cell into seven tasks, each closed by a driver
  green on the tablet
  ([`efec7bd`](https://github.com/dmenezesgabriel/jupydroid/commit/efec7bd82888507b8bea6bc7f3f314c56349fcaa))

- **prd**: Split the instrumented sweep into one task per package shard
  ([`6d9a931`](https://github.com/dmenezesgabriel/jupydroid/commit/6d9a931b0031be9c8167a6cdd0db76baf36829d8))

- **prd**: T1 closes the preload route, so gcompat is measured before the tracer
  ([`d17ae75`](https://github.com/dmenezesgabriel/jupydroid/commit/d17ae750c629324834bef36051bf455405905064))

- **prd**: T3 is split before any code, because the tracer is a task of its own
  ([`e23d1eb`](https://github.com/dmenezesgabriel/jupydroid/commit/e23d1eb5d4002bcec7d6e43747b5e050cb114204))

- **prd**: T3a closes - stock glibc starts under the patched tracer
  ([`c7413a3`](https://github.com/dmenezesgabriel/jupydroid/commit/c7413a3d4327deaf140347f9eb5114d06e3050e2))

- **prd**: T3b closes - a glibc CPython installs and imports PyPI's manylinux wheels
  ([`f7938c8`](https://github.com/dmenezesgabriel/jupydroid/commit/f7938c861277fa73582e78736dbf268a808c0729))

- **prd**: T5 closes - the second interpreter is the kernel's home, and the tracer's set is reworked
  whole
  ([`1421fe0`](https://github.com/dmenezesgabriel/jupydroid/commit/1421fe02c94305eb7e7edffff1113c6b921a004b))

- **prd**: T6 splits along the mechanism's own seams
  ([`58e035c`](https://github.com/dmenezesgabriel/jupydroid/commit/58e035c1ea1574821de5dc02ffab60bf69e37371))

- **prd**: T6a closes - the refusal mechanism proven per libc on the tablet
  ([`3d48df1`](https://github.com/dmenezesgabriel/jupydroid/commit/3d48df110a141f4b06a8285c5ceb8f02bdba78cc))

- **prd**: T6b closes - the second interpreter ships as an additive asset
  ([`540e65b`](https://github.com/dmenezesgabriel/jupydroid/commit/540e65b1302c009cb5d4652ba0929299cbabdc5d))

- **prd**: T6c closes - the kernel runs glibc and installs a manylinux wheel
  ([`3a6ac75`](https://github.com/dmenezesgabriel/jupydroid/commit/3a6ac75cf5452c224d1c721b055109d086219027))

- **prd**: T6d closes - the patched proot ships and the explanation reads manylinux
  ([`fe381a9`](https://github.com/dmenezesgabriel/jupydroid/commit/fe381a91849e6a5e1fb0092ad0140d892706b2b5))

- **prd**: Textual's mkdocs config is censused for site polish before the site ships
  ([`b864de2`](https://github.com/dmenezesgabriel/jupydroid/commit/b864de20c0346b5b0ce6a18a7e98b19f77915547))

- **prd**: The arm64 probe ran green in under four minutes (OPS-005 T5)
  ([`e8797e9`](https://github.com/dmenezesgabriel/jupydroid/commit/e8797e903cc605a3721b8b7aac0c6990971cf055))

- **prd**: The audit branch merged under the required checks (OPS-020 T2)
  ([`68dfbc9`](https://github.com/dmenezesgabriel/jupydroid/commit/68dfbc92b1aff5a4bf3bf7cf0cd23915b46c2f37))

- **prd**: The docs deploy went live on the merge push (OPS-020 T3)
  ([`fd46da6`](https://github.com/dmenezesgabriel/jupydroid/commit/fd46da6f43b259001e0517b25e8e463db54b3d37))

- **prd**: The first whole-suite run finished with 55 failures; T6 is not done
  ([`1800176`](https://github.com/dmenezesgabriel/jupydroid/commit/1800176e1597df3fe2aec8c686b76520483a6c2a))

- **prd**: The label under the icon reads Jupydroid, not the template's name
  ([`782a365`](https://github.com/dmenezesgabriel/jupydroid/commit/782a365ac434bc2ccb04b59f041534df774eee67))

- **prd**: The shown install script reads as code in a read-only cell field
  ([`a3b0ea7`](https://github.com/dmenezesgabriel/jupydroid/commit/a3b0ea7d75c9dff99fbdc2bcabe3fd560ac56bd1))

- **prd**: The textual census reads its split mkdocs config from the research clone
  ([`63ce59d`](https://github.com/dmenezesgabriel/jupydroid/commit/63ce59d268824e13ac7cd792ee0b9b66122640ea))

- **prd**: Tick AUD-014's first task
  ([`f146e08`](https://github.com/dmenezesgabriel/jupydroid/commit/f146e08a1b181c2a31b3a7670dfe63493c3247f4))

- **prd**: Tick JD-090 T1, the four wheels at their fixed versions
  ([`cf948db`](https://github.com/dmenezesgabriel/jupydroid/commit/cf948db926cccffaa48d084a5f0a388969c47467))

- **prd**: Tick JD-091 T1, and record anchorBounds as the box's outer bounds
  ([`46b5a00`](https://github.com/dmenezesgabriel/jupydroid/commit/46b5a00fdedfc3ca28e226a1bf4fcecac6ce58aa))

- **prd**: Tick JD-091 T2, and record the decorator anchor and the two fixture repairs
  ([`47332c7`](https://github.com/dmenezesgabriel/jupydroid/commit/47332c7aeb5aaa7857bce8a7da6279881e91b8a8))

- **prd**: Tick JD-092 T1, and record the at-HEAD classification on dev36
  ([`4411437`](https://github.com/dmenezesgabriel/jupydroid/commit/4411437395a8d8325f56073ab82647b588626838))

- **prd**: Tick JD-092 T2, and record the answered open decision
  ([`0a67ac6`](https://github.com/dmenezesgabriel/jupydroid/commit/0a67ac60c3456a85733d7940298407754b308561))

- **prd**: Write AUD-014's PRD
  ([`cac83e7`](https://github.com/dmenezesgabriel/jupydroid/commit/cac83e75f1dbddc5353a47616fa6235570094562))

- **prd**: Write JD-067's PRD and link it in the backlog
  ([`0a33d16`](https://github.com/dmenezesgabriel/jupydroid/commit/0a33d16af765bab220bec70be1fa45721a1019f3))

- **prd**: Write JD-068's PRD and link it in the backlog
  ([`a19de41`](https://github.com/dmenezesgabriel/jupydroid/commit/a19de41f5169add189487b94945cd9a1e41ded80))

- **prd**: Write JD-075's PRD and link it in the backlog
  ([`98e8cd2`](https://github.com/dmenezesgabriel/jupydroid/commit/98e8cd2405a104e492a483d61578e35a9caf1dd6))

- **prd**: Write JD-076's PRD and link it in the backlog
  ([`07c5081`](https://github.com/dmenezesgabriel/jupydroid/commit/07c50819e038e8997377aa1cb4b041fb71f90f5b))

- **prd**: Write JD-084's PRD and link it in the backlog
  ([`4af797a`](https://github.com/dmenezesgabriel/jupydroid/commit/4af797a8eeb7aa4af27eb6271fd37679bdb33e0c))

- **prd**: Write JD-085's PRD and link it in the backlog
  ([`ad9659e`](https://github.com/dmenezesgabriel/jupydroid/commit/ad9659ec2f6cc6022cc34b313758a7aa1859f926))

- **prd**: Write JD-086's PRD and link it in the backlog
  ([`de27381`](https://github.com/dmenezesgabriel/jupydroid/commit/de27381ddfbb8a446a7a1c26f549945210b32b65))

- **prd**: Write JD-087's PRD and link it in the backlog
  ([`8c1caea`](https://github.com/dmenezesgabriel/jupydroid/commit/8c1caea6a72c6e5f610086bf4f02343ad2fff0bb))

- **prd**: Write JD-088's PRD and link it in the backlog
  ([`8b13d16`](https://github.com/dmenezesgabriel/jupydroid/commit/8b13d163f34a3195264374d3f19d59a7f3f225b0))

- **prd**: Write JD-089's PRD and link it in the backlog
  ([`42a5275`](https://github.com/dmenezesgabriel/jupydroid/commit/42a527523b316a9875b510b4dfbbf4eeb659e193))

- **prd**: Write JD-090's PRD and link it in the backlog
  ([`05a2a8b`](https://github.com/dmenezesgabriel/jupydroid/commit/05a2a8b50350e16c80f81144c4b8aed6b09397b1))

- **prd**: Write JD-091's PRD, and correct GAP-207's numbers to the line's own
  ([`405cf70`](https://github.com/dmenezesgabriel/jupydroid/commit/405cf708363422353b52c8df8ddb36a899e5ce23))

- **prd**: Write JD-092's PRD — a clean instrumented suite on the emulator
  ([`4474364`](https://github.com/dmenezesgabriel/jupydroid/commit/447436468a2e2283812142627072991cd21ead79))

- **site**: A page per feature area, and OPS-012 ships
  ([`1789e10`](https://github.com/dmenezesgabriel/jupydroid/commit/1789e10168a6b571ab6a09f653598fafb1612663))

- **site**: Adopt the mkdocs knobs the textual census keeps (OPS-012 T9)
  ([`ced3067`](https://github.com/dmenezesgabriel/jupydroid/commit/ced3067c86165eb28de3ed562b2c6ba0af8a401b))

- **site**: Getting started walks install to the first cell, and OPS-011 ships
  ([`d868b4b`](https://github.com/dmenezesgabriel/jupydroid/commit/d868b4b33c94aaf4905b3bf7bd1f9e0d685d5cf7))

- **site**: Start the user documentation site with a strict MkDocs Material build (OPS-009 T1)
  ([`1d300af`](https://github.com/dmenezesgabriel/jupydroid/commit/1d300afef1ea270d38eeab329b744f13d91895ef))

- **site**: The Chat and MCP pages ship on the site-shot flow convention (OPS-012 T7)
  ([`fc8847a`](https://github.com/dmenezesgabriel/jupydroid/commit/fc8847af1ce9203e18f795ebf66e997dda6a7ba9))

- **site**: The Console and Terminal pages ship on the site-shot flow convention (OPS-012 T4)
  ([`58e570a`](https://github.com/dmenezesgabriel/jupydroid/commit/58e570a4040d7425438c65c6cbd0c7f012a83b53))

- **site**: The Files and Search pages ship on the site-shot flow convention (OPS-012 T5)
  ([`1747a57`](https://github.com/dmenezesgabriel/jupydroid/commit/1747a57065759c0a40cc885d3fb6bc2061c80319))

- **site**: The getting-started page shows the frames its steps describe (OPS-011 T3)
  ([`f4fa960`](https://github.com/dmenezesgabriel/jupydroid/commit/f4fa9601b11707ce4709ec6425109d58169553cf))

- **site**: The getting-started page walks a release install to the first cell (OPS-011 T1)
  ([`fd379a9`](https://github.com/dmenezesgabriel/jupydroid/commit/fd379a9e3281ee70fb256b11bfd93b8faaf62627))

- **site**: The Git page ships on the site-shot flow convention (OPS-012 T6)
  ([`a3e9c9c`](https://github.com/dmenezesgabriel/jupydroid/commit/a3e9c9c87a1f42d42b438e00981e3b7a325fe909))

- **site**: The Interface page ships on the site-shot flow convention (OPS-012 T2)
  ([`ab32e9b`](https://github.com/dmenezesgabriel/jupydroid/commit/ab32e9bb5562f90811b27fddf2faf72dabec2ba5))

- **site**: The landing page carries the tour in both colour schemes
  ([`4505eca`](https://github.com/dmenezesgabriel/jupydroid/commit/4505ecaac65a870e50a436fe6b4190333f9360cb))

- **site**: The Notebooks page ships on the site-shot flow convention (OPS-012 T3)
  ([`686b65e`](https://github.com/dmenezesgabriel/jupydroid/commit/686b65ea813d0dd027a2268bdd245557fd8c3951))

- **site**: The screenshot convention is ADR-0170 and OPS-010 ships
  ([`425fa39`](https://github.com/dmenezesgabriel/jupydroid/commit/425fa3934a0bfe204fdf79f2c22942e7ee310757))

- **site**: The Settings and Troubleshooting pages ship on the site-shot flow convention (OPS-012
  T8)
  ([`c22513c`](https://github.com/dmenezesgabriel/jupydroid/commit/c22513c115ceb231226e697c47b96466e8bdd4aa))

- **site**: The Spaces page ships on the site-shot flow convention (OPS-012 T1)
  ([`7aca58d`](https://github.com/dmenezesgabriel/jupydroid/commit/7aca58d06ac0e0bd759971e83b37a62f044e839d))

- **site**: Walk the built site in a browser and commit the frames (OPS-012 T10)
  ([`60b71bb`](https://github.com/dmenezesgabriel/jupydroid/commit/60b71bbe312783496cb819e8b17c381701ab7a03))

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

### Refactoring

- **autopilot**: Move the loop from scripts/claude-autopilot to scripts/autopilot
  ([`8093746`](https://github.com/dmenezesgabriel/jupydroid/commit/8093746f5967ea55c3661887918225659e4859c7))

- **extension-api**: Move glyphBesideText into the contract so an extension can size a glyph from
  its label
  ([`fedb8da`](https://github.com/dmenezesgabriel/jupydroid/commit/fedb8da9caa8cdb4120ec8f37355c17a5d3c47fa))

- **ui**: Move the terminal entry out of Navigation.kt beside its key
  ([`0a6f0ae`](https://github.com/dmenezesgabriel/jupydroid/commit/0a6f0aefc653f8b54d32f289ee336a18a34cd403))

### Testing

- **audit**: Add the three frames the popup and drawer reads are tested on
  ([`3ce9419`](https://github.com/dmenezesgabriel/jupydroid/commit/3ce94194457138536814729ef5fc2bf9c7594792))

- **licences**: The Source offer assertion names the file's heading, not wording it dropped
  ([`da7e8da`](https://github.com/dmenezesgabriel/jupydroid/commit/da7e8da8f361c8b9c094f8ac2d2ddbf1a271f6be))

- **maestro**: %pip installs duckdb and scikit-learn in a Space or the cell says why, so the item
  ships
  ([`bcf9312`](https://github.com/dmenezesgabriel/jupydroid/commit/bcf93123b62a331c60f230ca2a64533a9276bdb6))

- **maestro**: A contributed panel's read against a stalled Space server kills the app, named from
  the device's own log
  ([`7e97665`](https://github.com/dmenezesgabriel/jupydroid/commit/7e9766543bd7f3c31e74a0d0f085b6ec5f0239d8))

- **maestro**: A Space's glibc kernel installs duckdb from PyPI and answers after a relaunch
  ([`abdf6d8`](https://github.com/dmenezesgabriel/jupydroid/commit/abdf6d8772838fe2da059ff355b74b54216830d6))

- **maestro**: A stalled Space server's surfaces fail their reads with the app alive, so the item
  ships
  ([`92ef363`](https://github.com/dmenezesgabriel/jupydroid/commit/92ef363b47c896735da75b59743c9b773d135fe1))

- **maestro**: A stopped Space's Settings installs OpenCode and Claude, and OpenCode answers a Chat
  ask, so the item ships
  ([`761df5c`](https://github.com/dmenezesgabriel/jupydroid/commit/761df5c927f8479b3c8ad6c8331459fa649037ab))

- **maestro**: Focus the first cell by its field's name in five more tablet flows
  ([`abca1a6`](https://github.com/dmenezesgabriel/jupydroid/commit/abca1a651e6512bd0d3cd3d109759331ed8551c5))

- **maestro**: Focus the first cell by its field's name in five tablet flows
  ([`e422301`](https://github.com/dmenezesgabriel/jupydroid/commit/e4223019602457fc595ac050c2ddf531e2daad5d))

- **maestro**: Focus the first cell by its field's name in six more tablet flows
  ([`3a8b0f3`](https://github.com/dmenezesgabriel/jupydroid/commit/3a8b0f3fd6041c90542ed72003fc1cd91b387206))

- **maestro**: Focus the first cell by its field's name in the console-clear flow
  ([`23e96e5`](https://github.com/dmenezesgabriel/jupydroid/commit/23e96e5570baa8c85766cd0626151664cf47c2db))

- **maestro**: Focus the first cell by its field's name in the overwrite flow
  ([`f71d803`](https://github.com/dmenezesgabriel/jupydroid/commit/f71d8034b04c10d796f41ce12102aa176534409b))

- **maestro**: Leave a Space for the Spaces list from every surface, at both widths
  ([`48c5042`](https://github.com/dmenezesgabriel/jupydroid/commit/48c50426e01f9e2933c4bef8dfe39d3c07919d73))

- **maestro**: One cell-focus sub-flow takes the cell's index, and six flows type into later cells
  by name
  ([`a9a05c2`](https://github.com/dmenezesgabriel/jupydroid/commit/a9a05c27e7b3c33899d8560a3bd65e23f9cfa28d))

- **maestro**: Pip's message on the tablet reads the same for a package the platform cannot have and
  for a name that exists nowhere
  ([`92f7722`](https://github.com/dmenezesgabriel/jupydroid/commit/92f772279838e77e3861bd93b8b7ecbe1e8d6875))

- **maestro**: Read a finding's card-to-line gap off two UI dumps on the tablet
  ([`02eb1f8`](https://github.com/dmenezesgabriel/jupydroid/commit/02eb1f8f4a5e73ee04986d233981bdd9ad50a7a7))

- **maestro**: Read a line holding a breakpoint and a finding at both tablet widths
  ([`82b47ed`](https://github.com/dmenezesgabriel/jupydroid/commit/82b47ed16944f2d40e49bb3ecba35e1d4ed4adfb))

- **maestro**: Read the Alpine 3.23 rootfs's fixed versions, numpy and pip on the tablet, and ship
  the item
  ([`250a829`](https://github.com/dmenezesgabriel/jupydroid/commit/250a829f387e0216ccf03e5cdc5a7e88d70fb039))

- **maestro**: Settings' App icon row flips the launcher alias on the tablet, so the item ships
  ([`8392cfc`](https://github.com/dmenezesgabriel/jupydroid/commit/8392cfcca710275f8d70a3a95923c51576dc7a53))

- **maestro**: Tab-reload-demo expects a clean tab to follow its file, and focuses the inserted cell
  by name
  ([`ea8d859`](https://github.com/dmenezesgabriel/jupydroid/commit/ea8d8590e1880f7c3bb73090773b9eee47785ba8))

- **maestro**: The cell below a %pip install imports it, and does again after a relaunch, so the
  item ships
  ([`9251c14`](https://github.com/dmenezesgabriel/jupydroid/commit/9251c14b65c8b2713309b5e7bf3ba1616773b13a))

- **maestro**: The delete-stops-session demo follows a shut Recents section and focuses cells by
  name, and runs green on the tablet
  ([`b9e9922`](https://github.com/dmenezesgabriel/jupydroid/commit/b9e99220902307fcb3e537f4c9e896805c2785dd))

- **maestro**: The eight flows that cleared Recents follow a shut section, and run green on the
  tablet
  ([`e19ad59`](https://github.com/dmenezesgabriel/jupydroid/commit/e19ad597199ad53cad0cdb05bdee89573745bf8f))

- **maestro**: The error log's demo runs start to finish on dev36 as one driver, which reads the
  pulled log and finds both entries
  ([`f2840aa`](https://github.com/dmenezesgabriel/jupydroid/commit/f2840aa80d3f38787ac6f98407b0badf18d85443))

- **maestro**: The footer's demo runs on a phone and a tablet at font scale 1.0 and 2.0, so the item
  ships
  ([`c123b0e`](https://github.com/dmenezesgabriel/jupydroid/commit/c123b0e33780ee49f164b2e2b8099a5b73bc10ea))

- **maestro**: The rename drivers follow a tab's long-press menu and a shut Recents section, and run
  green on the tablet
  ([`c7e0c53`](https://github.com/dmenezesgabriel/jupydroid/commit/c7e0c53572b40c8173a8d1fe6a313148a15f1088))

- **maestro**: The Source Control panel says what is happening in every state, so the item ships
  ([`cf26444`](https://github.com/dmenezesgabriel/jupydroid/commit/cf26444782303dd71b42b3c221dc559eda263db9))

- **maestro**: The tab-rename flows follow a tab's long-press menu and run green on the tablet
  ([`08082c7`](https://github.com/dmenezesgabriel/jupydroid/commit/08082c76d27041904aadc2d6deaab13b78b7b134))

- **maestro**: The two chat flows focus a new notebook's cell by its name, and run green on the
  tablet, so the item ships
  ([`546da6f`](https://github.com/dmenezesgabriel/jupydroid/commit/546da6f9696d9bfc02e39173109046b4ac59bbe3))

- **maestro**: Three more flows type into later cells by name, and the output-caps scroll back up
  gets the time its swipes cost
  ([`441d3e8`](https://github.com/dmenezesgabriel/jupydroid/commit/441d3e832369b3ff32a78afeacdf890ebed96b79))

- **richtext**: Scan a runaway print over 5,000 repeats so a cold JVM stays under its CPU bound
  ([`9ea876e`](https://github.com/dmenezesgabriel/jupydroid/commit/9ea876e189a3a85d1969d2a950174a21f066525d))

- **scripts**: A cold dev36 boot's dialog is counted per boot by a driver that never presses Wait
  ([`ec583ab`](https://github.com/dmenezesgabriel/jupydroid/commit/ec583ab3da4fd1b5d976741f115912109563932c))

- **scripts**: A driver that runs the instrumented suite and reads the instrument log
  ([`d3eade8`](https://github.com/dmenezesgabriel/jupydroid/commit/d3eade8e0db58c786bb41e6119a4b8ead3b44e72))

- **scripts**: An android-36 image without Google APIs gave the dev36 dialog in 3 of 5 cold boots,
  the baseline's count
  ([`cc9d15e`](https://github.com/dmenezesgabriel/jupydroid/commit/cc9d15ec23152e803f60f90006e30ebb6f69749c))

- **scripts**: Holding Play services off gave the dev36 dialog in 5 of 5 cold boots, and the driver
  reads that disable back
  ([`4c2b91e`](https://github.com/dmenezesgabriel/jupydroid/commit/4c2b91e1952222d72b3fcc9d0d18d06fb0039f68))

- **scripts**: Neither duckdb (0 of 168 releases) nor scikit-learn (0 of 76) has a musllinux aarch64
  wheel, counted by a script against PyPI
  ([`dc2abd2`](https://github.com/dmenezesgabriel/jupydroid/commit/dc2abd27cb1116ce5c9c5cbf2b553467c727dfe0))

- **scripts**: The dev36 driver saves a snapshot only from a boot with no dialog and stops when /
  runs low
  ([`82289cf`](https://github.com/dmenezesgabriel/jupydroid/commit/82289cfe8aa8e095824f3547ffeb13b08e8e8b16))

- **scripts**: The instrumented driver samples window focus and keeps the run's logcat
  ([`402be8b`](https://github.com/dmenezesgabriel/jupydroid/commit/402be8b83e8c7469a82493ddb45af24564f05f27))

- **space**: A %pip install is invisible to the kernel that ran it, measured on the tablet
  ([`49ea830`](https://github.com/dmenezesgabriel/jupydroid/commit/49ea83082603e842a6e4cf176807bdc1c0aa73fb))

- **space**: A glibc CPython installs and imports PyPI's manylinux wheels, on the tablet
  ([`ae5c791`](https://github.com/dmenezesgabriel/jupydroid/commit/ae5c7919598453ab2f4e1b28fafeef30755d90f6))

- **space**: A musl CPython cannot import PyPI's manylinux wheels through gcompat, so the
  interpreter itself must be glibc's
  ([`25121bb`](https://github.com/dmenezesgabriel/jupydroid/commit/25121bbafd35fc6501d3389851cc4a82a8757b19))

- **space**: A Space server's restart archives the finished run's log on the arm64 tablet, so the
  rotation is proved where a Space runs
  ([`31e7707`](https://github.com/dmenezesgabriel/jupydroid/commit/31e7707146a6bcf73c7c8a6d4f4cd46655675284))

- **space**: All thirty-six must-list packages install from PyPI and run their quickstarts on the
  tablet
  ([`ffaf25a`](https://github.com/dmenezesgabriel/jupydroid/commit/ffaf25a067209e763ed7def46828e1e83bceb3ea))

- **space**: An existing Space gains the gnu interpreter additively, on the tablet
  ([`9b4e784`](https://github.com/dmenezesgabriel/jupydroid/commit/9b4e78441c96874fa0f59846e2802116ec360934))

- **space**: Both chat harnesses' cold installs are timed and sized on the tablet (JD-083 T4)
  ([`67abc27`](https://github.com/dmenezesgabriel/jupydroid/commit/67abc27dc5564750c0dac09d3d65651e2282267b))

- **space**: Stock glibc cannot start under the shipped runtime, because ld.so refuses
  set_robust_list before a preload can arm a handler
  ([`7f433d5`](https://github.com/dmenezesgabriel/jupydroid/commit/7f433d5a0aa1a35afdd534690d08d4c3e981df7d))

- **space**: Stock glibc's true and sh exit 0 under the patched tracer, on the tablet
  ([`297a01e`](https://github.com/dmenezesgabriel/jupydroid/commit/297a01ec82bdf58f85f471c0508980ecaf6c92b4))

- **space**: The glibc kernel installs and imports a published wheel on the tablet
  ([`2eb80b8`](https://github.com/dmenezesgabriel/jupydroid/commit/2eb80b8eafe8297c99fa72e40aa27d7d41918df6))

- **space**: The glibc research probes retire into the must-list demo
  ([`d40ab23`](https://github.com/dmenezesgabriel/jupydroid/commit/d40ab23fa2dbe8f87d91cb17159361802d89eaf3))

- **space**: The refusal mechanism proven per libc on the tablet
  ([`6dc1020`](https://github.com/dmenezesgabriel/jupydroid/commit/6dc10203c7fce34860b7a74581706f9c5ab1018d))

- **space**: The whole glibc image against the second interpreter, measured on the tablet
  ([`b15bdb1`](https://github.com/dmenezesgabriel/jupydroid/commit/b15bdb175023434e4f252209db99f1e85c6c65a5))

- **tabs**: A driver presses the middle of a floor-width tab on a phone and reads where its close's
  reach starts
  ([`1c442f7`](https://github.com/dmenezesgabriel/jupydroid/commit/1c442f703af570ebd6899b04553e6486e3bb5764))

- **terminal**: Measure the terminal's margin and its drawn pitch on the tablet
  ([`a713ad2`](https://github.com/dmenezesgabriel/jupydroid/commit/a713ad21c3897c42ff0006b54ffe0fe2b3f2c2a6))

- **terminal**: Name the terminal's margin once and pin what counts it
  ([`c56e739`](https://github.com/dmenezesgabriel/jupydroid/commit/c56e73908a7a6e7b238525bdb97c33fc505a0ddc))

- **ui**: Accept whole-pixel rounding in the bottom bar's centring check
  ([`e0a129c`](https://github.com/dmenezesgabriel/jupydroid/commit/e0a129cce9edc4f119b4bae020fbbb817eaa8db6))

- **ui**: Drop the squiggle test that measures the gutter mark the squiggle replaced
  ([`062872d`](https://github.com/dmenezesgabriel/jupydroid/commit/062872db2bb15d66f10f0d66762a1e6e8c7afbe3))

- **ui**: Read a cell's popups against the line and caret they anchor on
  ([`eb1074a`](https://github.com/dmenezesgabriel/jupydroid/commit/eb1074a9a3cfdd5b961d3925334986c818394803))

- **ui**: RichTextImageTest requires its 600dp column so a 320dp window cannot clamp it
  ([`d74c7d1`](https://github.com/dmenezesgabriel/jupydroid/commit/d74c7d13a2184c6a75331aedb55512a69e36803c))

- **ui**: The search chevron test measures each node's laid-out height, not the bounds its list
  clips
  ([`dfa9c03`](https://github.com/dmenezesgabriel/jupydroid/commit/dfa9c03296b293432987c1e7bf81934ed76c225d))

- **ui**: The sibling-close menu is pressed where a minimum-width tab owns the point
  ([`5ca0108`](https://github.com/dmenezesgabriel/jupydroid/commit/5ca0108c17e81abf29b04b90d7e5bf8c3f1aa00f))

- **ui**: The two-pane navigation tests state the window they need
  ([`eee1c99`](https://github.com/dmenezesgabriel/jupydroid/commit/eee1c99d963d0eae4bcbf88914b2ba389ccdb218))

- **ui**: Three window-bound instrumented tests measure what they name on a 320dp window
  ([`c652c8a`](https://github.com/dmenezesgabriel/jupydroid/commit/c652c8adf573b590e9ce008d431ab8a1db488c29))


## v0.1.1 (2026-11-06)

### Bug Fixes

- **release**: Check that the tag is on main from the checkout, without a fetch (JD-066 T5)
  ([`d76e756`](https://github.com/dmenezesgabriel/jupydroid/commit/d76e756f938784b15d609390c7408a7a1856e75a))

- **release**: Tag a merged release pull request from the release commit it merged (JD-066 T2)
  ([`96b2351`](https://github.com/dmenezesgabriel/jupydroid/commit/96b2351daef56e765ded921995e23d7796c21cb0))


## v0.1.0 (2026-11-05)

- Initial Release
