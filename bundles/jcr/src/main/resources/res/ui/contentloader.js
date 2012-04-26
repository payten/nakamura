var bundlesBody = null
var bundlesTemplate = null;

function renderBundles() {
  bundlesBody.empty();
  for (var i = 0; i < bundles.length; i++) {
    var evenOdd = (i % 2 == 0) ? 'even' : 'odd';
    var oddEven = (i % 2 == 0) ? 'odd' : 'even';
    var bundle = bundles[i];
    var tr = bundlesTemplate.clone();
    tr.attr('id', 'entry-'+bundle)
        .addClass(evenOdd)
        .removeClass(oddEven);
    tr.find('td:eq(0) input[type=checkbox]')
        .attr('id', 'bundle-name-'+bundle)
        .attr('name', 'bundle-name')
        .val(bundle);
    tr.find('td:eq(1)').text(bundle);
    tr.appendTo(bundlesBody);
  }
}

$(document).ready(function() {
  bundlesTable = $('#plugin_table').tablesorter({
      headers: {
        0: { sorter:"digit" },
        5: { sorter: false }
      },
      textExtraction:mixedLinksExtraction
    }).bind("sortEnd", function() {
      var t = bundlesTable.eq(0).attr("config");
      if (t.sortList) {
        setCookie("bundlelist", t.sortList);
      }
    });
  bundlesBody = bundlesTable.find('tbody');
  bundlesTemplate = bundlesBody.find('tr').clone();

  renderBundles();
});
