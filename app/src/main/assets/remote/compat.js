/* ES5 helpers for old tablet browsers (Android 4.x WebKit). */
var RemoteCompat = (function() {
  function xhr(method, url, body, contentType, callback) {
    var req = new XMLHttpRequest();
    req.open(method, url, true);
    if (contentType) {
      req.setRequestHeader('Content-Type', contentType);
    }
    req.onreadystatechange = function() {
      if (req.readyState !== 4) return;
      var ok = req.status >= 200 && req.status < 300;
      var text = req.responseText || '';
      var json = null;
      if (text) {
        try {
          json = JSON.parse(text);
        } catch (e) {}
      }
      callback(ok, json, text, req.status);
    };
    req.onerror = function() {
      callback(false, null, '', 0);
    };
    req.send(body != null ? body : null);
  }

  function getQueryParam(name) {
    var query = window.location.search;
    if (!query || query.charAt(0) !== '?') return null;
    var pairs = query.substring(1).split('&');
    for (var i = 0; i < pairs.length; i++) {
      var eq = pairs[i].indexOf('=');
      var key = eq >= 0 ? pairs[i].substring(0, eq) : pairs[i];
      if (decodeURIComponent(key.replace(/\+/g, ' ')) === name) {
        if (eq < 0) return '';
        return decodeURIComponent(pairs[i].substring(eq + 1).replace(/\+/g, ' '));
      }
    }
    return null;
  }

  function setHidden(el, hidden) {
    if (!el) return;
    if (hidden) {
      el.setAttribute('hidden', 'hidden');
      el.style.display = 'none';
    } else {
      el.removeAttribute('hidden');
      el.style.display = '';
    }
  }

  function hypot(x, y) {
    return Math.sqrt(x * x + y * y);
  }

  function firstFile(list) {
    return list && list.length ? list[0] : null;
  }

  function triggerDownload(url) {
    var iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = url;
    document.body.appendChild(iframe);
    setTimeout(function() {
      if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
    }, 120000);
  }

  return {
    xhr: xhr,
    get: function(url, cb) {
      xhr('GET', url, null, null, cb);
    },
    postJson: function(url, obj, cb) {
      xhr('POST', url, JSON.stringify(obj), 'application/json', cb);
    },
    postForm: function(url, formData, cb) {
      xhr('POST', url, formData, null, cb);
    },
    getQueryParam: getQueryParam,
    setHidden: setHidden,
    hypot: hypot,
    firstFile: firstFile,
    triggerDownload: triggerDownload
  };
})();
