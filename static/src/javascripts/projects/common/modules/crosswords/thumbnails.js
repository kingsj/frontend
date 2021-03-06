define([
    'bonzo',
    'qwery',
    'common/utils/_',
    'common/utils/$',
    'common/utils/ajax',
    'common/modules/crosswords/helpers',
    'common/modules/crosswords/persistence'
], function (
    bonzo,
    qwery,
    _,
    $,
    ajax,
    helpers,
    persistence
) {
    var textXOffset = 15,
        textYOffset = 19;

    function makeTextCells(savedState) {
        var columns = savedState.length,
            rows = savedState[0].length;

        return _.flatten(_.map(_.range(columns), function (column) {
            return _.map(_.range(rows), function (row) {
                var enteredText = savedState[column][row],
                    el,
                    top,
                    left;

                if (enteredText) {
                    el = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                    top = helpers.gridSize(row);
                    left = helpers.gridSize(column);

                    bonzo(el).attr({
                        x: left + textXOffset,
                        y: top + textYOffset,
                        'class': 'crossword__cell-text'
                    }).text(enteredText);

                    return [el];
                } else {
                    return [];
                }
            });
        }));
    }

    function init() {
        _.forEach(qwery('.js-crossword-thumbnail'), function (elem) {
            var $elem = bonzo(elem),
                savedState = persistence.loadGridState($elem.attr('data-crossword-id'));

            if (savedState) {
                ajax({
                    url: $elem.attr('src'),
                    type: 'xml',
                    method: 'get',
                    crossOrigin: true,
                    success: function (data) {
                        var cells = makeTextCells(savedState),
                            svg = qwery('svg', data)[0];
                        bonzo(svg).append(cells);
                        $elem.replaceWith(svg);
                    }
                });
            }
        });
    }

    return {
        init: init,
        makeTextCells: makeTextCells
    };
});
