// ============================================================
// RetroDroid — Landing Page Scripts
// i18n + Systems Grid + Pixel Canvas + Animations
// ============================================================

(function () {
  'use strict';

  // ================================================================
  // SYSTEM DATA — the nine bundled libretro cores
  // ================================================================
  var SYSTEMS = [
    { id: 'native32', icon: '🕹️', nameZh: 'Native32', nameEn: 'Native32', core: 'native32emu', lic: 'bsd',
      subZh: 'Sunplus SPG 芯片游戏', subEn: 'Sunplus SPG chip games',
      descZh: '运行在 Sunplus DVD 芯片栈式虚拟机上的经典电视游戏，从字节码到 MPEG-1 过场完整还原。',
      descEn: 'Classic TV games running on the stack VM of Sunplus DVD chips — fully recreated from bytecode to MPEG-1 cutscenes.' },
    { id: 'spmp8000', icon: '📟', nameZh: 'WooBooKong', nameEn: 'WooBooKong / SPMP8000', core: 'spmp8000', lic: 'bsd',
      subZh: 'SPMP8000 掌机游戏', subEn: 'SPMP8000 handheld games',
      descZh: '基于华芯 SPMP8000 芯片的国产掌机，还原它的彩色屏幕与内置游戏。',
      descEn: 'Handhelds built on the SPMP8000 chip, recreating their color screens and built-in games.' },
    { id: 'coolbar', icon: '🏎️', nameZh: 'Coolbar', nameEn: 'Coolbar (MTK)', core: 'coolbar', lic: 'bsd',
      subZh: 'MTK 方向盘游戏机', subEn: 'MTK steering-wheel consoles',
      descZh: '联发科方案的红白机风格方向盘电视游戏机，支持可选的方向配置文件。',
      descEn: 'MTK-based steering-wheel TV consoles, with an optional orientation profile.' },
    { id: 'dingoo', icon: '🎮', nameZh: 'Dingoo A320', nameEn: 'Dingoo A320', core: 'dingoo', lic: 'bsd',
      subZh: '经典开源掌机', subEn: 'The classic open handheld',
      descZh: '2010 年代经典的 A320 开源掌机平台，那个时代随身模拟游戏的记忆。',
      descEn: 'The iconic A320 open handheld platform — portable retro gaming of its era.' },
    { id: 'hyperscan', icon: '🃏', nameZh: 'HyperScan', nameEn: 'HyperScan', core: 'hyperscan', lic: 'bsd',
      subZh: 'Mattel RFID 主机', subEn: "Mattel's RFID console",
      descZh: '美泰 2006 年推出的 RFID 卡片游戏主机，市场存世稀少，如今在手机上复生。',
      descEn: "Mattel's 2006 RFID card console — rare in the wild, revived on your phone." },
    { id: 'playdia', icon: '💿', nameZh: 'Playdia', nameEn: 'Playdia', core: 'playdia', lic: 'bsd',
      subZh: '万代光盘主机', subEn: "Bandai's CD-based console",
      descZh: '万代 1994 年的 FMV 光盘主机，动画互动电影与教育游戏随 .cue 直接加载。',
      descEn: "Bandai's 1994 FMV console — interactive anime titles load straight from .cue sheets." },
    { id: 'bbk', icon: '📖', nameZh: '步步高电子词典', nameEn: 'BBK Dictionary', core: 'bbkemu', lic: 'gpl',
      subZh: 'a4980 / a4988 机型', subEn: 'a4980 / a4988 models',
      descZh: '一代人的课堂记忆：步步高电子词典上的学习游戏与隐藏彩蛋。',
      descEn: 'A classroom memory for a generation — study games and easter eggs on BBK dictionaries.' },
    { id: 'lavax', icon: '📚', nameZh: 'LavaX', nameEn: 'LavaX', core: 'lavax', lic: 'gpl',
      subZh: 'Lava X 学习掌机', subEn: 'Lava X learning handhelds',
      descZh: '运行 Lava 语言的学习型掌机，内置游戏与自制程序皆可运行。',
      descEn: 'Learning handhelds running the Lava language — stock games and homebrew alike.' },
    { id: 'wqx', icon: '🧮', nameZh: '文曲星 WQX', nameEn: 'WQX (Wenquxing)', core: 'wqx', lic: 'gpl',
      subZh: 'nc1020 / pc1000 / cc800 / nc2000 / nc3000', subEn: 'nc1020 / pc1000 / cc800 / nc2000 / nc3000',
      descZh: '从 NC1020 到 NC3000 的文曲星全机型，无需游戏内容，选择机型直接启动固件。',
      descEn: 'Every Wenquxing model from NC1020 to NC3000 — no game content needed, just boot the firmware.' }
  ];

  var LICENSES = [
    { id: 'all', zh: '全部', en: 'All' },
    { id: 'bsd', zh: 'BSD-3 核心', en: 'BSD-3 cores' },
    { id: 'gpl', zh: 'GPL-3.0 核心', en: 'GPL-3.0 cores' }
  ];

  // ================================================================
  // FAMILY DATA — sibling repositories
  // ================================================================
  var FAMILY = [
    { repo: 'Native32Emu', zh: 'Sunplus SPG 芯片游戏模拟器', en: 'Sunplus SPG chip game emulator' },
    { repo: 'SPMP8000Emu', zh: 'WooBooKong / SPMP8000 掌机模拟器', en: 'WooBooKong / SPMP8000 handheld emulator' },
    { repo: 'CoolbarEmu', zh: 'MTK 方向盘游戏机模拟器', en: 'MTK steering-wheel console emulator' },
    { repo: 'DingooEmu', zh: 'Dingoo A320 掌机模拟器', en: 'Dingoo A320 handheld emulator' },
    { repo: 'HyperScanEmu', zh: 'Mattel HyperScan 主机模拟器', en: "Mattel HyperScan console emulator" },
    { repo: 'PlaydiaEmu', zh: 'Bandai Playdia 主机模拟器', en: 'Bandai Playdia console emulator' },
    { repo: 'BBKEmu', zh: '步步高电子词典模拟器', en: 'BBK electronic dictionary emulator' },
    { repo: 'LavaXEmu', zh: 'Lava X 学习掌机模拟器', en: 'Lava X learning handheld emulator' },
    { repo: 'WQXEmu', zh: '文曲星电子词典模拟器', en: 'Wenquxing dictionary emulator' }
  ];

  // ================================================================
  // i18n — Translations
  // ================================================================
  var translations = {
    zh: {
      // meta
      'meta-title': 'RetroDroid — 一个 APK，九大经典系统',
      'meta-desc': 'RetroDroid：Android 全能 libretro 前端，一个 APK 内置九大模拟器核心，无需在应用内下载',
      // nav
      'nav-systems': '支持系统',
      'nav-features': '特性',
      'nav-quickstart': '快速上手',
      'nav-family': '模拟器家族',
      // hero
      'hero-tag': 'AloysHF 模拟器家族 · Android 全能前端',
      'hero-subtitle': '一个 APK，九大经典系统，应用内零下载',
      'hero-desc': '所有模拟器核心随 APK 一并内置、随应用一同更新——从 Sunplus 芯片游戏到文曲星电子词典，一台手机全部搞定。',
      'hero-download': '下载 APK',
      'hero-github': '查看源码',
      'hero-join': '加入社区',
      'hero-scroll': '向下滚动探索',
      // stats
      'stat-systems': '模拟系统',
      'stat-systems-sub': '核心全部内置',
      'stat-apk': '个 APK',
      'stat-apk-sub': '安装即玩',
      'stat-dl': '应用内下载',
      'stat-dl-sub': '离线也完整可用',
      'stat-slots': '即时存档槽位',
      'stat-slots-sub': '带预览图与自动存档',
      // about
      'about-title': 'RetroDroid 是什么？',
      'about-p1': 'RetroDroid 是 <strong>AloysHF 模拟器家族</strong>的 Android 门户：它自己不模拟任何硬件，而是把家族中的九个模拟器核心以 libretro 核心的形式打包进同一个 APK。',
      'about-p2': '游戏与固件放在设备上固定的文件夹里，打开应用直接选择即可游玩——没有核心下载器，没有联网配置，一切开箱即用。',
      'about-point1': '核心随 APK 一起更新，永远保持一致',
      'about-point2': '系统列表由每个核心的 android.json 档案自动生成',
      'about-point3': '不内置任何游戏或固件，只运行你自己的内容',
      'chip-slots': '5 个存档槽位 · 带预览',
      'chip-auto': '退出自动存档',
      // architecture
      'arch-title': '技术架构',
      'arch-subtitle': '从 Compose 界面到 libretro 核心，四层清晰分明',
      'arch-ui-label': '界面层',
      'arch-ui-sub': '系统列表 · 固定本机游戏库 · 暂停菜单 · 存档管理',
      'arch-jni-sub': 'Kotlin 与 Rust 之间的桥接层',
      'arch-fe-label': '前端层',
      'arch-chip-video': '视频 blit',
      'arch-chip-audio': '音频环形缓冲',
      'arch-chip-input': '输入',
      'arch-chip-save': '即时存档',
      'arch-cores-label': '核心层',
      'arch-cores-sub': '9 个核心以 .so 形式打包在 APK 的 jniLibs 中，运行时按需 dlopen',
      // systems
      'sys-title': '支持系统',
      'sys-subtitle': '九个模拟器核心，随 APK 一并内置',
      // features
      'feat-title': '核心特性',
      'feat-subtitle': '为掌上游戏库而设计的每一个细节',
      'feat-bundle-title': '核心全内置',
      'feat-bundle-desc': '九个模拟器核心全部打包进 APK，没有任何应用内下载或联网校验，装好就能离线畅玩。',
      'feat-lib-title': '固定本机游戏库',
      'feat-lib-desc': '游戏放在 /sdcard/RetroDroid 固定目录下，首次启动自动建好目录树，放入文件刷新即见。',
      'feat-save-title': '即时存档与续玩',
      'feat-save-desc': '五个存档槽位带画面预览；退出游戏时自动保存，下次启动询问是否从上次继续。',
      'feat-pause-title': '强大暂停菜单',
      'feat-pause-desc': '游戏内截图、主题/方向/旋转/缩放等画面设置与核心选项，全部集中在暂停菜单里。',
      'feat-folder-title': '文件夹式浏览',
      'feat-folder-desc': '子文件夹排在游戏之前、逐层进入，返回手势逐层退出——再大的游戏库也井井有条。',
      'feat-profile-title': '档案即系统',
      'feat-profile-desc': '每个核心用一份 android.json 档案描述自己，应用据此生成系统注册表——接入新系统只需核心加档案。',
      // quickstart
      'qs-title': '快速上手',
      'qs-subtitle': '三步开始，或者从源码自己构建',
      'qs-install': '安装',
      'qs-install-1': '下载并安装 APK',
      'qs-install-1-sub': 'Releases 提供 arm64-v8a / x86_64 单 ABI 与 universal 版本',
      'qs-install-2': '授予「所有文件访问」权限',
      'qs-install-2-sub': '首次启动时会自动创建 RetroDroid 目录树',
      'qs-install-3': '选择游戏，开玩',
      'qs-install-3-sub': 'WQX 没有游戏内容，直接选择机型启动固件',
      'qs-library': '放置游戏',
      'qs-lib-1': '把游戏放进对应系统目录',
      'qs-lib-2': '固件放在 BIOS 目录',
      'qs-lib-3': '刷新游戏列表',
      'qs-lib-3-sub': '文件名区分大小写；光盘游戏把 .cue 与轨道放在一起',
      'qs-build': '从源码构建',
      'qs-build-1': '准备 Rust + NDK r27+ + JDK 17',
      'qs-build-2': '一条脚本构建全部',
      'qs-build-3': '或本地调试核心',
      // family
      'fam-title': 'AloysHF 模拟器家族',
      'fam-subtitle': 'RetroDroid 的每个核心，都来自这些独立项目',
      'fam-note': 'RetroDroid 本体采用 BSD-3-Clause 许可；GPL-3.0 核心以独立动态库形式打包，完整源码发布在各自的仓库中。',
      // community
      'com-title': '加入社区',
      'com-subtitle': '反馈问题、交流玩法、追踪最新进展',
      'com-discord-desc': '国际社区，与全球玩家交流',
      'com-qq-desc': '中文社区，交流更接地气',
      'com-join': '立即加入 →',
      'com-join-qq': '一键加群 →',
      // footer
      'footer-desc': 'AloysHF 模拟器家族的 Android 全能 libretro 前端',
      'footer-project': '项目',
      'footer-nightly': 'Nightly 构建',
      'footer-contract': '核心契约',
      'footer-community': '社区',
      'footer-family': '家族',
      'footer-all-repos': '全部仓库 →',
      'footer-legal': 'RetroDroid 不包含任何游戏或主机固件，用户只能运行自己拥有版权的内容。各核心保留其原始许可证。',
      'footer-copy': 'BSD 3-Clause License &copy; 2026 Aloys. Built with 🦀 Rust + ☕ Kotlin.'
    },
    en: {
      // meta
      'meta-title': 'RetroDroid — One APK, nine classic systems',
      'meta-desc': 'RetroDroid: an all-in-one libretro frontend for Android. One APK with nine emulator cores bundled — no in-app downloads',
      // nav
      'nav-systems': 'Systems',
      'nav-features': 'Features',
      'nav-quickstart': 'Quick Start',
      'nav-family': 'Family',
      // hero
      'hero-tag': 'AloysHF emulator family · Android frontend',
      'hero-subtitle': 'One APK, nine classic systems, zero in-app downloads',
      'hero-desc': 'Every emulator core ships inside the APK and updates together with the app — from Sunplus chip games to Wenquxing dictionaries, all on one phone.',
      'hero-download': 'Download APK',
      'hero-github': 'View Source',
      'hero-join': 'Join Community',
      'hero-scroll': 'Scroll to explore',
      // stats
      'stat-systems': 'Emulated systems',
      'stat-systems-sub': 'All cores bundled',
      'stat-apk': 'APK',
      'stat-apk-sub': 'Install and play',
      'stat-dl': 'In-app downloads',
      'stat-dl-sub': 'Fully usable offline',
      'stat-slots': 'Save state slots',
      'stat-slots-sub': 'With previews & auto-save',
      // about
      'about-title': 'What is RetroDroid?',
      'about-p1': 'RetroDroid is the <strong>Android gateway to the AloysHF emulator family</strong>: it emulates nothing itself — instead, it packages the nine emulator cores of the family as libretro cores inside a single APK.',
      'about-p2': 'Games and firmware live in one fixed folder on your device. Open the app, pick a title and play — no core downloader, no online setup, everything works out of the box.',
      'about-point1': 'Cores update together with the APK, always in sync',
      'about-point2': 'The system list is generated from each core\'s android.json profile',
      'about-point3': 'No games or firmware bundled — it only runs your own content',
      'chip-slots': '5 save slots · with previews',
      'chip-auto': 'Auto-save on exit',
      // architecture
      'arch-title': 'Architecture',
      'arch-subtitle': 'Four clean layers, from Compose UI to libretro cores',
      'arch-ui-label': 'UI',
      'arch-ui-sub': 'System list · fixed on-device library · pause menu · save management',
      'arch-jni-sub': 'The bridge between Kotlin and Rust',
      'arch-fe-label': 'Frontend',
      'arch-chip-video': 'Video blit',
      'arch-chip-audio': 'Audio ring buffer',
      'arch-chip-input': 'Input',
      'arch-chip-save': 'Save states',
      'arch-cores-label': 'Cores',
      'arch-cores-sub': '9 cores packaged as .so libraries in the APK\'s jniLibs, dlopened on demand',
      // systems
      'sys-title': 'Supported Systems',
      'sys-subtitle': 'Nine emulator cores, all bundled in the APK',
      // features
      'feat-title': 'Core Features',
      'feat-subtitle': 'Every detail designed for a handheld game library',
      'feat-bundle-title': 'All Cores Bundled',
      'feat-bundle-desc': 'Nine emulator cores ship inside the APK — no in-app downloads, no online checks. Install once and play offline.',
      'feat-lib-title': 'Fixed On-Device Library',
      'feat-lib-desc': 'Games live under the fixed /sdcard/RetroDroid folder. The folder tree is created on first launch — drop files in and refresh.',
      'feat-save-title': 'Save States & Resume',
      'feat-save-desc': 'Five save state slots with screenshot previews; leaving a game writes an automatic save and the next launch offers to continue.',
      'feat-pause-title': 'Powerful Pause Menu',
      'feat-pause-desc': 'In-game screenshots, theme/orientation/rotation/scaling settings and core options — all in one pause menu.',
      'feat-folder-title': 'Folder Browsing',
      'feat-folder-desc': 'Subfolders are listed before games and open one level at a time; the back gesture steps out one level — large libraries stay tidy.',
      'feat-profile-title': 'Profiles as Systems',
      'feat-profile-desc': 'Every core describes itself with an android.json profile that builds the system registry — adding a system is adding a core and a profile.',
      // quickstart
      'qs-title': 'Quick Start',
      'qs-subtitle': 'Three steps to play, or build from source',
      'qs-install': 'Install',
      'qs-install-1': 'Download and install the APK',
      'qs-install-1-sub': 'Releases ship per-ABI (arm64-v8a / x86_64) and universal builds',
      'qs-install-2': 'Grant "All files access"',
      'qs-install-2-sub': 'The RetroDroid folder tree is created automatically on first launch',
      'qs-install-3': 'Pick a game and play',
      'qs-install-3-sub': 'WQX has no game content — pick a model and start the firmware',
      'qs-library': 'Add Games',
      'qs-lib-1': 'Drop games into the system folder',
      'qs-lib-2': 'Firmware goes into the BIOS folder',
      'qs-lib-3': 'Refresh the game list',
      'qs-lib-3-sub': 'Names are case-sensitive; for disc games keep the .cue next to its tracks',
      'qs-build': 'Build from Source',
      'qs-build-1': 'Rust + NDK r27+ + JDK 17',
      'qs-build-2': 'One script builds everything',
      'qs-build-3': 'Or debug cores locally',
      // family
      'fam-title': 'The AloysHF Emulator Family',
      'fam-subtitle': 'Every RetroDroid core comes from one of these projects',
      'fam-note': 'RetroDroid itself is BSD-3-Clause. The GPL-3.0 cores are bundled as separate dynamic libraries with their complete sources published in their own repositories.',
      // community
      'com-title': 'Join the Community',
      'com-subtitle': 'Report issues, share tips, follow the latest progress',
      'com-discord-desc': 'The international community — chat with players worldwide',
      'com-qq-desc': 'The Chinese community — chat in your language',
      'com-join': 'Join now →',
      'com-join-qq': 'Join the group →',
      // footer
      'footer-desc': 'The all-in-one Android libretro frontend of the AloysHF emulator family',
      'footer-project': 'Project',
      'footer-nightly': 'Nightly builds',
      'footer-contract': 'Core Contract',
      'footer-community': 'Community',
      'footer-family': 'Family',
      'footer-all-repos': 'All repositories →',
      'footer-legal': 'RetroDroid does not include games or console firmware. Users must own the content they run. Bundled cores keep their own licenses.',
      'footer-copy': 'BSD 3-Clause License &copy; 2026 Aloys. Built with 🦀 Rust + ☕ Kotlin.'
    }
  };

  var currentLang = localStorage.getItem('rd-lang') || (navigator.language && navigator.language.startsWith('zh') ? 'zh' : 'en');

  // ================================================================
  // i18n — Apply translations
  // ================================================================
  function applyLang(lang) {
    currentLang = lang;
    localStorage.setItem('rd-lang', lang);
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';

    var t = translations[lang];

    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var key = el.getAttribute('data-i18n');
      if (t[key] === undefined) return;
      if (el.tagName === 'TITLE' || el.tagName === 'META') return;
      el.innerHTML = t[key];
    });

    if (t['meta-title']) document.title = t['meta-title'];
    var metaDesc = document.querySelector('meta[name="description"]');
    if (metaDesc && t['meta-desc']) metaDesc.setAttribute('content', t['meta-desc']);

    var langBtn = document.getElementById('lang-toggle');
    if (langBtn) langBtn.textContent = lang === 'zh' ? 'EN' : '中';

    buildSystems();
    buildFamily();
  }

  // ================================================================
  // SYSTEMS GRID — tabs + cards
  // ================================================================
  var currentLic = 'all';

  function buildSystems() {
    var container = document.getElementById('systems-dynamic');
    if (!container) return;

    var html = '<div class="systems-tabs">';
    LICENSES.forEach(function (lic) {
      var count = lic.id === 'all' ? SYSTEMS.length : SYSTEMS.filter(function (s) { return s.lic === lic.id; }).length;
      var label = currentLang === 'zh' ? lic.zh : lic.en;
      var active = lic.id === currentLic ? ' active' : '';
      html += '<button class="systems-tab' + active + '" data-lic="' + lic.id + '">' + label + ' (' + count + ')</button>';
    });
    html += '</div>';

    var list = currentLic === 'all' ? SYSTEMS : SYSTEMS.filter(function (s) { return s.lic === currentLic; });
    html += '<div class="systems-grid">';
    list.forEach(function (s) {
      var name = currentLang === 'zh' ? s.nameZh : s.nameEn;
      var sub = currentLang === 'zh' ? s.subZh : s.subEn;
      var desc = currentLang === 'zh' ? s.descZh : s.descEn;
      html += '<div class="system-card">';
      html += '  <div class="system-head">';
      html += '    <span class="system-icon">' + s.icon + '</span>';
      html += '    <span class="license-chip ' + (s.lic === 'bsd' ? 'lic-bsd' : 'lic-gpl') + '">' + (s.lic === 'bsd' ? 'BSD-3' : 'GPL-3.0') + '</span>';
      html += '  </div>';
      html += '  <h3>' + name + '</h3>';
      html += '  <div class="system-core">' + s.core + ' · ' + sub + '</div>';
      html += '  <p>' + desc + '</p>';
      html += '</div>';
    });
    html += '</div>';

    container.innerHTML = html;

    container.querySelectorAll('.systems-tab').forEach(function (tab) {
      tab.addEventListener('click', function () {
        currentLic = tab.getAttribute('data-lic');
        buildSystems();
      });
    });
  }

  // ================================================================
  // FAMILY GRID
  // ================================================================
  function buildFamily() {
    var grid = document.getElementById('family-grid');
    if (!grid) return;
    var html = '';
    FAMILY.forEach(function (f) {
      var desc = currentLang === 'zh' ? f.zh : f.en;
      html += '<a class="family-card" href="https://github.com/AloysHF/' + f.repo + '" target="_blank" rel="noopener">';
      html += '  <span class="family-card-name">' + f.repo + '<small>' + desc + '</small></span>';
      html += '  <span class="arrow">→</span>';
      html += '</a>';
    });
    grid.innerHTML = html;
  }

  // ================================================================
  // NAVBAR — scroll effect + mobile toggle
  // ================================================================
  var navbar = document.getElementById('navbar');

  function onScroll() {
    navbar.classList.toggle('scrolled', window.scrollY > 50);
  }

  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  var toggle = document.querySelector('.nav-toggle');
  var navLinks = document.querySelector('.nav-links');

  if (toggle && navLinks) {
    toggle.addEventListener('click', function () {
      navLinks.classList.toggle('open');
    });
    navLinks.querySelectorAll('a').forEach(function (a) {
      a.addEventListener('click', function () { navLinks.classList.remove('open'); });
    });
  }

  // ================================================================
  // SCROLL REVEAL — Intersection Observer
  // ================================================================
  var fadeEls = document.querySelectorAll('.fade-in-up');

  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

    fadeEls.forEach(function (el) { observer.observe(el); });
  } else {
    fadeEls.forEach(function (el) { el.classList.add('visible'); });
  }

  // ================================================================
  // ANIMATED COUNTER
  // ================================================================
  var statNumbers = document.querySelectorAll('.stat-number[data-target]');

  function animateCounter(el) {
    var target = parseInt(el.dataset.target, 10);
    var suffix = el.dataset.suffix || '';
    var duration = 1500;
    var start = performance.now();

    function tick(now) {
      var elapsed = now - start;
      var progress = Math.min(elapsed / duration, 1);
      var eased = 1 - Math.pow(1 - progress, 3);
      el.textContent = Math.round(eased * target).toLocaleString() + suffix;
      if (progress < 1) requestAnimationFrame(tick);
    }

    requestAnimationFrame(tick);
  }

  if ('IntersectionObserver' in window) {
    var statObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          animateCounter(entry.target);
          statObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.5 });

    statNumbers.forEach(function (el) { statObserver.observe(el); });
  } else {
    statNumbers.forEach(function (el) { animateCounter(el); });
  }

  // ================================================================
  // PIXEL CANVAS — hero background: glowing LCD matrix cells
  // ================================================================
  var canvas = document.getElementById('pixel-canvas');
  if (canvas && canvas.getContext) {
    var ctx = canvas.getContext('2d');
    var w, h, cells, CELL = 46;

    function resize() {
      w = canvas.width = canvas.offsetWidth;
      h = canvas.height = canvas.offsetHeight;
      initCells();
    }

    function initCells() {
      cells = [];
      var cols = Math.ceil(w / CELL);
      var rows = Math.ceil(h / CELL);
      for (var i = 0; i < cols; i++) {
        for (var j = 0; j < rows; j++) {
          // only a fraction of cells ever light up
          if (Math.random() < 0.16) {
            cells.push({
              x: i * CELL + CELL / 2,
              y: j * CELL + CELL / 2,
              life: Math.random() * -3,           // staggered start
              speed: 0.4 + Math.random() * 0.8,
              size: 2 + Math.random() * 3,
              green: Math.random() < 0.6
            });
          }
        }
      }
    }

    function draw() {
      ctx.clearRect(0, 0, w, h);

      cells.forEach(function (c) {
        c.life += c.speed * 0.016;
        if (c.life > 1) {
          c.life = Math.random() * -6;            // respawn after a dark pause
          c.green = Math.random() < 0.6;
        }
        if (c.life <= 0) return;

        // pulse in then out
        var alpha = Math.sin(c.life * Math.PI) * 0.55;
        if (alpha <= 0.01) return;

        var color = c.green ? '143, 255, 74' : '53, 224, 240';
        ctx.fillStyle = 'rgba(' + color + ', ' + alpha + ')';
        ctx.fillRect(c.x - c.size / 2, c.y - c.size / 2, c.size, c.size);
      });

      requestAnimationFrame(draw);
    }

    resize();
    draw();
    window.addEventListener('resize', resize);
  }

  // ================================================================
  // SMOOTH SCROLL
  // ================================================================
  document.querySelectorAll('a[href^="#"]').forEach(function (anchor) {
    anchor.addEventListener('click', function (e) {
      var target = document.querySelector(anchor.getAttribute('href'));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });

  // ================================================================
  // LANGUAGE TOGGLE
  // ================================================================
  var langBtn = document.getElementById('lang-toggle');
  if (langBtn) {
    langBtn.addEventListener('click', function () {
      applyLang(currentLang === 'zh' ? 'en' : 'zh');
    });
  }

  // ================================================================
  // INIT
  // ================================================================
  applyLang(currentLang);

  // Re-observe dynamically added elements after first build
  if ('IntersectionObserver' in window) {
    setTimeout(function () {
      document.querySelectorAll('.fade-in-up:not(.visible)').forEach(function (el) {
        observer.observe(el);
      });
    }, 100);
  }

})();
