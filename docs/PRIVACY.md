# mrowser Private: privacy behavior and limits

This variant starts from upstream `v1.4.0` and uses the separate Android package `net.mrowser.private`.

## Data the app does not save as browsing history

- There is no visit-history repository, JSON serializer, history file, or history screen. Navigation and page-title callbacks update the currently visible address only; they do not write URLs or titles to disk or logs.
- The WebView back/forward list is kept in the live WebView for navigation during the current session. It is not saved/restored with Android view state. Opening a fresh page from the home screen clears the previous in-memory list.
- Both address/search fields start blank, clear after leaving/returning to the home screen, and have Android view-state saving disabled. Their input type requests that the keyboard suppress suggestions.
- Android Autofill is excluded from the address fields and from the WebView and its descendants on Android 8.0 and later. Android Content Capture is excluded on Android 11 and later. WebView form-data/password saving is also disabled where the platform still honors those settings.
- The app does not write visited URLs or page titles to preferences, logs, or its own history file. Subtitle-fetch errors are logged without the subtitle URL, which may contain a signed token.
- The upstream release checker and updater have been removed. This app does not query the upstream GitHub release API or propose an official APK.

## Data intentionally kept

- `filesDir/favorites.json` stores favorite titles and URLs. Favorites remain editable, reorderable, and importable/exportable. They are intentionally durable user data, not a visit list.
- `filesDir/settings.json` stores browser preferences and the registrable domains on which the user explicitly allowed ads. That allow-list is a site-specific setting and may identify a domain the user chose; it does not store full page URLs, titles, or search terms.
- Cookies and website storage remain enabled. This preserves authenticated sessions and compatibility with streaming sites. JavaScript, DOM storage, service workers, and site-managed storage may let a website keep its own data, including searches or recommendations. The app does not clear cookies or website data.
- The WebView uses `LOAD_NO_CACHE` so it does not reuse its HTTP resource cache. Websites and service workers may still maintain their own caches or persist data in their own storage.
- When the user exports favorites, the exported JSON file is saved to Downloads. This is an explicit action and contains only favorites.

## Android, keyboard, websites, and external services

The browser can suppress its own history and request that Android Autofill and the keyboard avoid suggestions, but it cannot guarantee that every installed TV keyboard honors `textNoSuggestions`. Voice input, a third-party keyboard, or an Autofill service manually invoked by the user may process typed text outside the browser. Android's system components may also retain diagnostics beyond the app's control.

The address-bar suggestion flag applies to the app's two URL fields. Search suggestions rendered by a website, a search provider, or a signed-in account come from that website or service, not a local mrowser history list. The app cannot prevent those providers from receiving a submitted query or saving it remotely.

Cookies, site storage, service-worker caches, and website form behavior are deliberately preserved for compatibility. A website can itself show remembered values or keep data in its account, cookies, local storage, cache, or server. Those behaviors are separate from mrowser's removed history store.

The app still makes the network requests required to load sites, stream content, and fetch a subtitle selected for playback. The block list is bundled locally; the private build has no analytics, telemetry, advertisement SDK, or automatic update check.

## App identity and updates

`net.mrowser.private` has an independent Android data directory and can be installed alongside official mrowser. Existing data from `net.mrowser` is not read or changed. To transfer bookmarks, export them from the original app and import the file in mrowser Private.

Release APKs use a key dedicated to this variant. Keep the keystore and passwords backed up securely; Android will only accept a future in-place update signed with the same key. No private update service is configured in this source tree, so install future builds supplied by the private maintainer. Do not use upstream APKs as updates for this package.
