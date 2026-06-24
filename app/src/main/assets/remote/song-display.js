// Keep in sync with SongDisplay.kt (title/key, notes preview, placeholder marker).
var SongDisplay = (function() {
  var PREVIEW_LEN = 20;
  var PLACEHOLDER_MARKER = ' \uD83D\uDEA7';

  function preview(text) {
    var trimmed = (text || '').replace(/^\s+|\s+$/g, '');
    return trimmed.length <= PREVIEW_LEN ? trimmed : trimmed.slice(0, PREVIEW_LEN) + '\u2026';
  }

  function keySuffix(keySignature) {
    var key = (keySignature || '').replace(/^\s+|\s+$/g, '');
    return key ? ' (' + key + ')' : '';
  }

  function adjustedSongTitle(title, keySignature) {
    return title + keySuffix(keySignature);
  }

  function notesLine(notes) {
    return preview(notes);
  }

  function addPlaceholderPrompt(title) {
    return 'Add placeholder page: \u201c' + title + '\u201d' + PLACEHOLDER_MARKER;
  }

  return {
    PLACEHOLDER_MARKER: PLACEHOLDER_MARKER,
    adjustedSongTitle: adjustedSongTitle,
    notesLine: notesLine,
    addPlaceholderPrompt: addPlaceholderPrompt
  };
})();
