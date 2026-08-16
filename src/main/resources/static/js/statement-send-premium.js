(function () {
  function norm(text) {
    return String(text || '').replace(/\s+/g, ' ').trim();
  }

  function findButton(text) {
    var nodes = document.querySelectorAll('button, a');
    for (var i = 0; i < nodes.length; i++) {
      if (norm(nodes[i].textContent).indexOf(text) >= 0) {
        return nodes[i];
      }
    }
    return null;
  }

  function findHeading(text) {
    var nodes = document.querySelectorAll('h1, h2, h3, h4, div, p, span');
    for (var i = 0; i < nodes.length; i++) {
      if (norm(nodes[i].textContent) === text || norm(nodes[i].textContent).indexOf(text) >= 0) {
        return nodes[i];
      }
    }
    return null;
  }

  function commonAncestor(nodes) {
    if (!nodes || !nodes.length) return null;
    var first = nodes[0];
    while (first) {
      var ok = true;
      for (var i = 1; i < nodes.length; i++) {
        if (!first.contains(nodes[i])) {
          ok = false;
          break;
        }
      }
      if (ok) return first;
      first = first.parentElement;
    }
    return null;
  }

  function closestWithTable(node) {
    var current = node;
    while (current) {
      if (current.querySelector && current.querySelector('table')) return current;
      current = current.parentElement;
    }
    return null;
  }

  document.addEventListener('DOMContentLoaded', function () {
    var shareBtn = findButton('이미지로 바로 공유');
    var pngBtn = findButton('PNG 다운로드');
    var pdfBtn = findButton('PDF 다운로드');
    var viewBtn = findButton('명세서 보기');
    var monthInput = document.querySelector('input[type="month"]');
    var vendorSelect = document.querySelector('select');

    if (!shareBtn || !viewBtn || !monthInput || !vendorSelect) return;

    document.body.classList.add('statement-send-premium');

    if (viewBtn) viewBtn.classList.add('statement-primary-btn');
    if (shareBtn) shareBtn.classList.add('statement-primary-btn');
    if (pngBtn) pngBtn.classList.add('statement-secondary-btn');
    if (pdfBtn) pdfBtn.classList.add('statement-outline-btn');

    var toolbar = commonAncestor([shareBtn, pngBtn || shareBtn, pdfBtn || shareBtn]);
    if (toolbar) toolbar.classList.add('statement-action-row');

    var filterCard = commonAncestor([monthInput, vendorSelect, viewBtn]);
    if (filterCard) filterCard.classList.add('statement-filter-card');

    var h1 = document.querySelector('h1');
    if (h1) {
      h1.classList.add('statement-page-title');
      var kicker = document.createElement('div');
      kicker.className = 'statement-page-kicker';
      kicker.textContent = '거래처 명세서';
      if (!h1.previousElementSibling || !h1.previousElementSibling.classList || !h1.previousElementSibling.classList.contains('statement-page-kicker')) {
        h1.parentNode.insertBefore(kicker, h1);
      }
    }

    var desc = h1 && h1.nextElementSibling ? h1.nextElementSibling : null;
    if (desc) desc.classList.add('statement-page-desc');

    var docTitle = findHeading('거 래 명 세 서') || findHeading('거래명세서');
    var previewCard = docTitle ? closestWithTable(docTitle) : null;
    if (previewCard) {
      previewCard.classList.add('statement-preview-card');
      var paper = document.createElement('div');
      paper.className = 'statement-paper';
      while (previewCard.firstChild) {
        paper.appendChild(previewCard.firstChild);
      }
      previewCard.appendChild(paper);
    }

    var historyTitle = findHeading('문자 발송완료');
    if (historyTitle) {
      var historyCard = historyTitle.parentElement;
      while (historyCard && !historyCard.querySelector('table') && norm(historyCard.textContent).indexOf('발송완료') < 0) {
        historyCard = historyCard.parentElement;
      }
      if (!historyCard) historyCard = historyTitle.parentElement;
      if (historyCard) {
        historyCard.classList.add('statement-history-card');

        var countText = '0곳';
        var badgeNode = null;
        var spans = historyCard.querySelectorAll('span, div, strong');
        for (var s = 0; s < spans.length; s++) {
          var t = norm(spans[s].textContent);
          if (/^[0-9]+곳$/.test(t)) {
            countText = t;
            badgeNode = spans[s];
            break;
          }
        }

        var header = document.createElement('div');
        header.className = 'statement-history-header';
        header.innerHTML = '<div><h3 class="statement-history-title">문자 발송완료</h3><p class="statement-history-sub">해당 월에 문자로 보낸 거래처를 한눈에 관리합니다.</p></div><div class="statement-history-badge">' + countText + '</div>';

        var body = document.createElement('div');
        body.className = 'statement-history-body';

        var table = historyCard.querySelector('table');
        var errorLike = norm(historyCard.textContent).indexOf('불러오지 못했습니다') >= 0;

        if (table) {
          body.appendChild(table);
        } else {
          var empty = document.createElement('div');
          empty.className = 'statement-history-empty';
          empty.innerHTML = '<div class="empty-icon">✓</div><div>' + (errorLike ? '발송완료 기록을 불러오지 못했습니다.' : '아직 발송완료 기록이 없습니다.') + '</div>';
          body.appendChild(empty);
        }

        historyCard.innerHTML = '';
        historyCard.appendChild(header);
        historyCard.appendChild(body);
      }
    }
  });
})();
