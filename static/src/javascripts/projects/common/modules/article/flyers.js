define([
    'qwery',
    'common/utils/_',
    'common/utils/$',
    'common/utils/ajax',
    'common/modules/ui/images'
], function (
    qwery,
    _,
    $,
    ajax,
    imagesModule
) {
    function upgradeFlyer(el) {
        var href = $('a', el).attr('href'),
            matches = href.match(/(?:^https?:\/\/(?:www\.|m\.code\.dev-)theguardian\.com)?(\/.*)/);

        if (matches && matches[1]) {
            ajax({
                url: '/embed/card' + matches[1] + '.json',
                crossOrigin: true
            }).then(function (response) {
                $(el).html(response.html)
                    .removeClass('element-rich-link--not-upgraded')
                    .addClass('element-rich-link--upgraded');
                imagesModule.upgrade(el);
            });
        }
    }

    function init() {
        $('.js-article__body .element-rich-link').each(upgradeFlyer);
    }

    return {
        init: init,
        upgradeFlyer: upgradeFlyer
    };
});
