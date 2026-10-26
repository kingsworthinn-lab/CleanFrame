/* Android TV device layer. Replaces app/device.js in the Android build only.
   Talks to DeviceBridge.java through window.CleanFrameAndroid. ES5, like the rest of the runtime. */
(function () {
  'use strict';
  var bridge = window.CleanFrameAndroid;
  /* CleanFrame's built-in apps, by Android TV package name. Brands without an Android TV app are omitted. */
  var IDS = {
    stremio: 'com.stremio.one', netflix: 'com.netflix.ninja', disneyplus: 'com.disney.disneyplus',
    youtube: 'com.google.android.youtube.tv', appletv: 'com.apple.atve.androidtv.appletv',
    primevideo: 'com.amazon.amazonvideo.livingroom'
  };
  var EXTRA_BRANDS = { 'com.spotify.tv.android': 'spotify', 'com.wbd.stream': 'hbomax', 'com.hbo.hbonow': 'hbomax', 'com.tubitv': 'tubi' };
  var STREAMING = ['com.showmax.app', 'com.multichoice.dstvnow', 'com.dstv.now.android', 'com.plexapp.android', 'org.xbmc.kodi',
    'com.google.android.youtube.tvmusic', 'com.google.android.youtube.tvkids', 'com.britbox.tv', 'com.apple.android.music'];
  var BRAND_OF = {};
  Object.keys(IDS).forEach(function (brand) { BRAND_OF[IDS[brand]] = brand; });

  function unavailable(done) {
    done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The web preview cannot control the TV.' });
  }
  /* Bridge calls are synchronous; answer on the next tick so callers behave as they do on Samsung. */
  function answer(done, result, what) {
    setTimeout(function () {
      if (result === '') done(null);
      else if (result === 'missing') done({ message: what + ' is not installed on this box.', detail: 'Install it from Google Play, or remove it from Home in Settings.' });
      else done({ message: 'The box could not open this screen.', detail: String(result || 'Try again, or press the remote’s Home button to return to CleanFrame.') });
    }, 0);
  }
  function call(method, arg, what, done) {
    var result;
    if (!bridge) { unavailable(done); return; }
    try { result = arg === undefined ? bridge[method]() : bridge[method](arg); }
    catch (err) { result = err && err.message || 'Unknown error'; }
    answer(done, result, what);
  }
  function packageFor(id) { return IDS[id] || id; }
  function installed(id) {
    try { return !!bridge && bridge.installed(packageFor(id)); } catch (e) { return false; }
  }
  function launchApp(id, done) { call('launch', packageFor(id), 'This app', done); }
  function launch(brand, done) {
    if (!IDS[brand]) { done({ message: 'This app has no launch target.' }); return; }
    launchApp(IDS[brand], done);
  }
  function home(done) { call('home', undefined, 'Android TV Home', done); }
  function settings(done) { call('settings', undefined, 'Settings', done); }
  function apps(done) {
    var list, primary = Object.keys(IDS).map(function (b) { return IDS[b]; });
    if (!bridge) { unavailable(done); return; }
    try { list = JSON.parse(bridge.apps()); }
    catch (err) { setTimeout(function () { done({ message: 'Could not read installed apps.', detail: err && err.message || '' }); }, 0); return; }
    var result = list.filter(function (a) { return a && typeof a.appId === 'string' && a.name; }).map(function (a) {
      var brand = BRAND_OF[a.appId] || EXTRA_BRANDS[a.appId] || '';
      var rank = BRAND_OF[a.appId] ? primary.indexOf(a.appId) : STREAMING.indexOf(a.appId) >= 0 || EXTRA_BRANDS[a.appId] ? 20 : 30;
      return { appId: a.appId, name: a.name, brand: brand, icon: '/appicon/' + a.appId + '.png', rank: rank };
    });
    result.sort(function (a, b) { return a.rank - b.rank || a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
    setTimeout(function () { done(null, result); }, 0);
  }

  /* MainActivity forwards the remote's Back button here as Escape (keyCode 27). */
  window.CleanFrameAndroidKey = function (type, code) {
    var ev = document.createEvent('Event');
    ev.initEvent(type === 'up' ? 'keyup' : 'keydown', true, true);
    Object.defineProperty(ev, 'keyCode', { value: code });
    Object.defineProperty(ev, 'key', { value: code === 27 ? 'Escape' : '' });
    document.dispatchEvent(ev);
  };

  window.FrameDevice = {
    launch: launch, launchApp: launchApp, home: home, settings: settings, apps: apps,
    installed: installed, ids: IDS, platform: 'android',
    /* No art: Art Mode belongs to the Frame itself, not to a box plugged into it. */
    system: { name: 'Android TV', home: 'Android TV Home', homeDetail: 'Open the box’s usual Home screen',
      settings: 'Box settings', settingsDetail: 'Network, display, sound and apps', value: 'Home and box settings' }
  };
}());
