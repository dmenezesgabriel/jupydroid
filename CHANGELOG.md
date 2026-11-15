# CHANGELOG

<!-- version list -->

## v0.2.0 (2026-11-15)

### Bug Fixes

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

- **chat**: Drop MCP servers under New chat when the two would squeeze its label
  ([`70b96fe`](https://github.com/dmenezesgabriel/jupydroid/commit/70b96fefa0cf9d612c0ae8a0191ebcc82bb33ec0))

- **editor**: Draw a bottom sheet's status-bar icons for the app's theme
  ([`5f9011c`](https://github.com/dmenezesgabriel/jupydroid/commit/5f9011cb8be853fc504540f8a2100d29493cf19e))

- **editor**: Name the code field so a screen reader says what it edits
  ([`ff25248`](https://github.com/dmenezesgabriel/jupydroid/commit/ff25248db44ef65baa8a82dd589cbb64d13e946c))

- **explorer**: State a Files row's text colours so the open row stops reading 4.10:1
  ([`319dd2a`](https://github.com/dmenezesgabriel/jupydroid/commit/319dd2a06602ee3c81426b98b634edb01d2a741a))

- **launcher**: Collapse the empty cwd heading's margins into one
  ([`2dad03a`](https://github.com/dmenezesgabriel/jupydroid/commit/2dad03ab79c0be95a7bb469c5363a117725071b4))

- **launcher**: Grow a card to its label and its row at large font scales
  ([`8ddeacd`](https://github.com/dmenezesgabriel/jupydroid/commit/8ddeacdd0ccf9cc78e9f06f4eb7fb7591cc94028))

- **rename**: Keep Cancel and Rename above the keyboard when the refusal grows the dialog
  ([`518800b`](https://github.com/dmenezesgabriel/jupydroid/commit/518800b03e8cd07ae5bc1dae9b88d1ddfe3f0b3b))

- **running**: Keep each row's close button at one x whatever the name's length
  ([`7df0dd7`](https://github.com/dmenezesgabriel/jupydroid/commit/7df0dd76fad9e36f5f508bac91b700dc3fad96d6))

- **running**: Reserve the accordion header's 48dp so it stops borrowing the toolbar button's
  ([`99fcdf7`](https://github.com/dmenezesgabriel/jupydroid/commit/99fcdf7b2f96550738e0b7d713cbba2cd618585b))

- **scripts**: The autopilot loop sees a killed background task and survives a restart after stop
  ([`d7c22c4`](https://github.com/dmenezesgabriel/jupydroid/commit/d7c22c4a6fa7a5b67b00902abddce65b23f3ea43))

- **settings**: Size each segment to its label so no word breaks at large font scales
  ([`0de2361`](https://github.com/dmenezesgabriel/jupydroid/commit/0de2361c98ad86a410a92abb45612176625fee8e))

- **shell**: Consume the status bar's inset once, for the document under the tab strip
  ([`143d524`](https://github.com/dmenezesgabriel/jupydroid/commit/143d524ea4e3e61fc332ced33b7d3d24b98cf295))

- **shell**: Scroll the tab strip to the active tab
  ([`3371c9d`](https://github.com/dmenezesgabriel/jupydroid/commit/3371c9dd1eb5be63eb134bc9c3aa7af5c7522c0f))

- **spaces**: Drop a row's controls under its name when they would squeeze the status word
  ([`e9ae194`](https://github.com/dmenezesgabriel/jupydroid/commit/e9ae194b1a37bfaadc70a9d71bbe15706ddacb37))

- **terminal**: Keep Ctrl and Alt apart like the other extra keys at large font scales
  ([`5d64848`](https://github.com/dmenezesgabriel/jupydroid/commit/5d648480de3874208ee0114cede609b3296cfe29))

- **terminal**: Name the terminal's input so a screen reader says what it types into
  ([`9a80a9b`](https://github.com/dmenezesgabriel/jupydroid/commit/9a80a9becbae59fb1b9d7216c2155e3114f467d4))

- **ui**: Count the terminal cell at the pitch a run of glyphs steps
  ([`6471c96`](https://github.com/dmenezesgabriel/jupydroid/commit/6471c96f030d59547b36a22dbd0d2b5357798a47))

- **ui**: Count, draw and map a terminal cell at the font's fractional advance
  ([`e52a30b`](https://github.com/dmenezesgabriel/jupydroid/commit/e52a30b6d212313b606b8df6b2f1df892091d42b))

- **ui**: Draw a side panel's unstated text in sideBar.foreground
  ([`5dc9ca0`](https://github.com/dmenezesgabriel/jupydroid/commit/5dc9ca008a5110f2e58210b606378ce2cbf6e5f9))

### Build System

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

### Documentation

- **adr**: A control carries its own name and a text field the name of what it edits
  ([`e9f9b70`](https://github.com/dmenezesgabriel/jupydroid/commit/e9f9b70c83367d9a3776d1b985064009a15feb2d))

- **adr**: Correct the cost of the maestro gate tests and say why they are not scoped
  ([`a2d3799`](https://github.com/dmenezesgabriel/jupydroid/commit/a2d37992558b5bab23eb52c852f0598b2ca7cc94))

- **adr**: Correct what skipping the Gradle hooks saves, and record what a commit still costs
  ([`95259cf`](https://github.com/dmenezesgabriel/jupydroid/commit/95259cfdf7483deab02585564b2b918189f9cc75))

- **adr**: Measure the dialog margin on both devices and record it
  ([`20b2796`](https://github.com/dmenezesgabriel/jupydroid/commit/20b2796eb52255222480d9579f9db8fb9bdba51a))

- **adr**: Record the keyboard focus ring's mark, colour and width
  ([`73576b3`](https://github.com/dmenezesgabriel/jupydroid/commit/73576b34c7e67a6ee5e5dd5c2fc85974c23600e7))

- **adr**: Record why an icon beside text follows the text's font size
  ([`dd7fca3`](https://github.com/dmenezesgabriel/jupydroid/commit/dd7fca3cf6e8276c96605024f564269980d39281))

- **adr**: Record why the way back to the Spaces list is a named control, and ship the item
  ([`f4ca743`](https://github.com/dmenezesgabriel/jupydroid/commit/f4ca743bd786d058e0c0f39852a6eb0bd6345e41))

- **adr**: The list pane takes the library's width-class width
  ([`a0e1b0e`](https://github.com/dmenezesgabriel/jupydroid/commit/a0e1b0ec1afd8e7f5e7972295530faf1967a7c01))

- **adr**: The tab strip's controls keep the strip's height
  ([`3a96fd4`](https://github.com/dmenezesgabriel/jupydroid/commit/3a96fd451acb014419b4814827ef007e9230ed6f))

- **adr**: The terminal's margin is symmetric and the grid is counted inside it
  ([`c8c4cb6`](https://github.com/dmenezesgabriel/jupydroid/commit/c8c4cb62f33dac8c67f821d7af78ece06afc7493))

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

- **backlog**: Record the off-centre bottom bar as a gap and queue its repair next
  ([`25c427b`](https://github.com/dmenezesgabriel/jupydroid/commit/25c427b6dee4820a616882a10c4fca5e2ddafab2))

- **prd**: Measure the shared gutter cell on a diagnosed line and settle the finding's target
  ([`861aa00`](https://github.com/dmenezesgabriel/jupydroid/commit/861aa0018862fcba71488ee6e575b79d96613b4b))

- **prd**: Settle where JD-075's way back to Spaces lives and what leaving does today
  ([`0de7d2a`](https://github.com/dmenezesgabriel/jupydroid/commit/0de7d2a1c0069e5095db3ee10f9a81e730dad3b3))

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

- **prd**: Tick AUD-014's first task
  ([`f146e08`](https://github.com/dmenezesgabriel/jupydroid/commit/f146e08a1b181c2a31b3a7670dfe63493c3247f4))

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

### Features

- **ai**: Draw the focus ring on the chat panel's buttons
  ([`2ba29f9`](https://github.com/dmenezesgabriel/jupydroid/commit/2ba29f98297d7949c67b87c8b712643162a46af0))

- **ai**: Size the chat panel's glyphs from their labels so they keep their ratio at large font
  scales
  ([`09fc84d`](https://github.com/dmenezesgabriel/jupydroid/commit/09fc84db1a1d8a62ecde7a0f62e4af145c79e3fd))

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

- **extension-api**: Draw the focus ring on the dialogs every extension asks the host for
  ([`5e719d4`](https://github.com/dmenezesgabriel/jupydroid/commit/5e719d4ac406f7924af8c64b6808eab28c291d11))

- **extension-api**: Keep a 24dp margin beside Delete and Rename's dialogs
  ([`8e38a32`](https://github.com/dmenezesgabriel/jupydroid/commit/8e38a327369e113cb191d285f1fd30bf800d1bb7))

- **release**: Install the signed v0.1.1 release APK on the tablet and run a cell (JD-066 T6)
  ([`9c61207`](https://github.com/dmenezesgabriel/jupydroid/commit/9c61207573f2ae626b3a77798793b4a8be6fe95f))

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

### Refactoring

- **extension-api**: Move glyphBesideText into the contract so an extension can size a glyph from
  its label
  ([`fedb8da`](https://github.com/dmenezesgabriel/jupydroid/commit/fedb8da9caa8cdb4120ec8f37355c17a5d3c47fa))

- **ui**: Move the terminal entry out of Navigation.kt beside its key
  ([`0a6f0ae`](https://github.com/dmenezesgabriel/jupydroid/commit/0a6f0aefc653f8b54d32f289ee336a18a34cd403))

### Testing

- **audit**: Add the three frames the popup and drawer reads are tested on
  ([`3ce9419`](https://github.com/dmenezesgabriel/jupydroid/commit/3ce94194457138536814729ef5fc2bf9c7594792))

- **maestro**: Leave a Space for the Spaces list from every surface, at both widths
  ([`48c5042`](https://github.com/dmenezesgabriel/jupydroid/commit/48c50426e01f9e2933c4bef8dfe39d3c07919d73))

- **richtext**: Scan a runaway print over 5,000 repeats so a cold JVM stays under its CPU bound
  ([`9ea876e`](https://github.com/dmenezesgabriel/jupydroid/commit/9ea876e189a3a85d1969d2a950174a21f066525d))

- **terminal**: Measure the terminal's margin and its drawn pitch on the tablet
  ([`a713ad2`](https://github.com/dmenezesgabriel/jupydroid/commit/a713ad21c3897c42ff0006b54ffe0fe2b3f2c2a6))

- **terminal**: Name the terminal's margin once and pin what counts it
  ([`c56e739`](https://github.com/dmenezesgabriel/jupydroid/commit/c56e73908a7a6e7b238525bdb97c33fc505a0ddc))


## v0.1.1 (2026-11-06)

### Bug Fixes

- **release**: Check that the tag is on main from the checkout, without a fetch (JD-066 T5)
  ([`d76e756`](https://github.com/dmenezesgabriel/jupydroid/commit/d76e756f938784b15d609390c7408a7a1856e75a))

- **release**: Tag a merged release pull request from the release commit it merged (JD-066 T2)
  ([`96b2351`](https://github.com/dmenezesgabriel/jupydroid/commit/96b2351daef56e765ded921995e23d7796c21cb0))


## v0.1.0 (2026-11-05)

- Initial Release
