// Keep in sync with SongDisplay.kt (title/key, notes preview, placeholder marker).
const SongDisplay = (() => {
  const PREVIEW_LEN = 20;
  const PLACEHOLDER_MARKER = ' 🚧';

  function preview(text) {
    const trimmed = (text || '').trim();
    return trimmed.length <= PREVIEW_LEN ? trimmed : trimmed.slice(0, PREVIEW_LEN) + '…';
  }

  function keySuffix(keySignature) {
    const key = (keySignature || '').trim();
    return key ? ` (${key})` : '';
  }

  function adjustedSongTitle(title, keySignature, isPlaceholder) {
    return title + keySuffix(keySignature) + (isPlaceholder ? PLACEHOLDER_MARKER : '');
  }

  function notesLine(notes) {
    return preview(notes);
  }

  function addPlaceholderPrompt(title) {
    return `Add placeholder page: “${title}”${PLACEHOLDER_MARKER}`;
  }

  return {
    PLACEHOLDER_MARKER,
    adjustedSongTitle,
    notesLine,
    addPlaceholderPrompt,
  };
})();
