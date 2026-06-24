/* Shared song upload overlay (ES5). */
var RemoteUpload = (function() {
  var overlay = null;
  var form = null;
  var titleInput = null;
  var keyInput = null;
  var notesInput = null;
  var fileInput = null;
  var errorEl = null;
  var submitBtn = null;
  var dropZone = null;
  var dropText = null;
  var dropFile = null;
  var selectedFile = null;
  var uploadUrl = '';
  var onSuccess = null;
  var isBlocked = null;
  var submitting = false;

  function isAllowedFile(file) {
    if (!file) return false;
    var type = file.type || '';
    if (type.indexOf('image/') === 0 || type === 'application/pdf') return true;
    return /\.(pdf|png|jpe?g|gif|webp)$/i.test(file.name);
  }

  function preventDrag(e) {
    if (e.preventDefault) e.preventDefault();
    e.returnValue = false;
  }

  function clearFile() {
    selectedFile = null;
    fileInput.value = '';
    dropZone.className = '';
    RemoteCompat.setHidden(dropText, false);
    RemoteCompat.setHidden(dropFile, true);
    dropFile.textContent = '';
  }

  function applyFilenameSuggestions(filename) {
    RemoteCompat.get('/api/parse-filename?raw=' + encodeURIComponent(filename), function(ok, parsed) {
      if (ok && parsed) {
        titleInput.value = parsed.title || '';
        keyInput.value = parsed.key || '';
        notesInput.value = parsed.notes || '';
      } else {
        titleInput.value = filename.replace(/\.[^.]+$/, '').replace(/_/g, ' ');
        keyInput.value = '';
        notesInput.value = '';
      }
    });
  }

  function setFile(file) {
    if (!isAllowedFile(file)) {
      errorEl.textContent = 'Choose a PDF or image.';
      return false;
    }
    selectedFile = file;
    dropZone.className = 'has-file';
    RemoteCompat.setHidden(dropText, true);
    RemoteCompat.setHidden(dropFile, false);
    dropFile.textContent = file.name;
    applyFilenameSuggestions(file.name);
    errorEl.textContent = '';
    return true;
  }

  function ensureDom() {
    if (overlay) return;
    overlay = document.createElement('div');
    overlay.id = 'remoteUploadOverlay';
    overlay.setAttribute('hidden', 'hidden');
    overlay.style.display = 'none';
    overlay.innerHTML =
      '<form id="remoteUploadForm" novalidate>' +
      '<h2>Upload song</h2>' +
      '<label for="remoteUploadTitle">Title</label>' +
      '<input id="remoteUploadTitle" type="text" autocomplete="off" />' +
      '<label for="remoteUploadKey">Key</label>' +
      '<input id="remoteUploadKey" type="text" autocomplete="off" />' +
      '<label for="remoteUploadNotes">Notes</label>' +
      '<input id="remoteUploadNotes" type="text" autocomplete="off" />' +
      '<label for="remoteUploadFile">File (PDF or image)</label>' +
      '<div id="remoteUploadDropZone" role="button" tabindex="0" aria-label="Drop or choose file">' +
      '<span id="remoteUploadDropText">Drop PDF or image here, or tap to choose</span>' +
      '<span id="remoteUploadDropFile" hidden></span>' +
      '</div>' +
      '<input id="remoteUploadFile" type="file" accept="image/*,application/pdf,.pdf" hidden />' +
      '<div id="remoteUploadError"></div>' +
      '<div id="remoteUploadActions">' +
      '<button id="remoteUploadCancel" type="button">Cancel</button>' +
      '<button id="remoteUploadSubmit" type="submit">Save</button>' +
      '</div>' +
      '</form>';
    document.body.appendChild(overlay);

    form = document.getElementById('remoteUploadForm');
    titleInput = document.getElementById('remoteUploadTitle');
    keyInput = document.getElementById('remoteUploadKey');
    notesInput = document.getElementById('remoteUploadNotes');
    fileInput = document.getElementById('remoteUploadFile');
    errorEl = document.getElementById('remoteUploadError');
    submitBtn = document.getElementById('remoteUploadSubmit');
    dropZone = document.getElementById('remoteUploadDropZone');
    dropText = document.getElementById('remoteUploadDropText');
    dropFile = document.getElementById('remoteUploadDropFile');

    document.getElementById('remoteUploadCancel').addEventListener('click', close);
    dropZone.addEventListener('click', function() { fileInput.click(); });
    dropZone.addEventListener('keydown', function(e) {
      e = e || window.event;
      var key = e.key || e.keyCode;
      if (key === 'Enter' || key === ' ' || key === 32) {
        if (e.preventDefault) e.preventDefault();
        fileInput.click();
      }
    });
    dropZone.addEventListener('dragenter', function(e) {
      preventDrag(e);
      dropZone.className = 'drag-over';
    });
    dropZone.addEventListener('dragover', function(e) {
      preventDrag(e);
      dropZone.className = 'drag-over';
    });
    dropZone.addEventListener('dragleave', function(e) {
      preventDrag(e);
      dropZone.className = selectedFile ? 'has-file' : '';
    });
    dropZone.addEventListener('drop', function(e) {
      preventDrag(e);
      dropZone.className = selectedFile ? 'has-file' : '';
      var dt = e.dataTransfer || (e.originalEvent && e.originalEvent.dataTransfer);
      var file = dt ? RemoteCompat.firstFile(dt.files) : null;
      if (file) setFile(file);
    });
    fileInput.addEventListener('change', function() {
      var file = RemoteCompat.firstFile(fileInput.files);
      if (file) setFile(file);
    });
    form.addEventListener('submit', onSubmit);
  }

  function open() {
    if (isBlocked && isBlocked()) return;
    if (!uploadUrl) return;
    ensureDom();
    errorEl.textContent = '';
    titleInput.value = '';
    keyInput.value = '';
    notesInput.value = '';
    clearFile();
    RemoteCompat.setHidden(overlay, false);
  }

  function close() {
    if (!overlay) return;
    RemoteCompat.setHidden(overlay, true);
    errorEl.textContent = '';
  }

  function onSubmit(e) {
    if (e.preventDefault) e.preventDefault();
    e.returnValue = false;
    if (submitting || (isBlocked && isBlocked())) return false;
    var file = selectedFile || RemoteCompat.firstFile(fileInput.files);
    if (!file) {
      errorEl.textContent = 'Choose a PDF or image.';
      return false;
    }
    submitting = true;
    submitBtn.disabled = true;
    errorEl.textContent = '';
    var formData = new FormData();
    formData.append('title', titleInput.value.replace(/^\s+|\s+$/g, ''));
    formData.append('key', keyInput.value.replace(/^\s+|\s+$/g, ''));
    formData.append('notes', notesInput.value.replace(/^\s+|\s+$/g, ''));
    formData.append('filename', file.name);
    formData.append('mime', file.type || 'application/octet-stream');
    formData.append('file', file);
    RemoteCompat.postForm(uploadUrl, formData, function(ok, body) {
      if (ok) {
        close();
        if (onSuccess) onSuccess(body);
      } else {
        errorEl.textContent = (body && body.error) ? body.error : 'Upload failed';
      }
      submitting = false;
      submitBtn.disabled = false;
    });
    return false;
  }

  return {
    attach: function(opts) {
      uploadUrl = opts.uploadUrl || '';
      onSuccess = opts.onSuccess || null;
      isBlocked = opts.isBlocked || null;
      ensureDom();
      if (opts.trigger) {
        opts.trigger.addEventListener('click', open);
      }
    },
    open: open,
    close: close,
    setUploadUrl: function(url) { uploadUrl = url; }
  };
})();
