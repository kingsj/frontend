define([
    'bean',
    'bonzo',
    'qwery',
    'common/utils/$',
    'common/utils/template',
    'text!facia/views/button-show-more.html'
], function (
    bean,
    bonzo,
    qwery,
    $,
    template,
    showMoreBtn
) {
    return function (container) {
        var $container           = bonzo(container),
            itemsHiddenOnDesktop = qwery('.js-hide', $container).length > 0,
            itemsHiddenOnMobile  = qwery('.js-hide-on-mobile', $container).length > 0,
            className            = 'fc-show-more--hidden',
            $button              = null;

        this.addShowMoreButton = function () {
            $button = $.create(template(showMoreBtn, {
                type: this.getContainerTitle()
            }));

            if (itemsHiddenOnMobile || itemsHiddenOnDesktop) {
                if (!itemsHiddenOnDesktop) {
                    $container.addClass('fc-show-more--mobile-only');
                }

                $container.addClass(className)
                    .append($button)
                    .removeClass('js-container--fc-show-more');
                bean.on($button[0], 'click', showMore);
            }
        };

        this.getContainerTitle = function () {
            return $container.data('title') || '';
        };

        function showMore() {
            /**
             * Do not remove: it should retain context for the click stream module, which recurses upwards through
             * DOM nodes.
             */
            $button.hide();
            $container.removeClass(className);
        }
    };
});