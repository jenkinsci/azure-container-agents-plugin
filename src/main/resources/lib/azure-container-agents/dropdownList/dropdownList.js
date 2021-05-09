/*
* Update dropdownList base on other UI controller.
*/
function updateDropdownListView (e, url, config) {

    if (url == null) return;
    config = config || {};
    config = object(config);
    var originalOnSuccess = config.onSuccess;
    var l = $(e);

    config.onSuccess = function (rsp) {
        var selectedOption = rsp.responseText;

        for (i=0; i<l.childNodes.length; i++) {
            if (selectedOption == l.childNodes[i].value) {
                l.selectedIndex = i;
                break;
            }
        }
        if (originalOnSuccess!=undefined)
            originalOnSuccess(rsp);
        l.removeClassName("select-ajax-pending");
    };

    config.onFailure = function (rsp) {
        l.removeClassName("select-ajax-pending");
    };

    l.addClassName("select-ajax-pending");
    new Ajax.Request(url, config);
}


Behaviour.specify("SELECT.dropdownList", 'select', 1, function(e) {

    if (e == null) return;
    refillOnChange(e,function(params) {
        updateDropdownListView(e,e.getAttribute("fillUrl"),{
            parameters: params,
            onSuccess: function() {
                fireEvent(e,"change");
            }
        });
    });


});
