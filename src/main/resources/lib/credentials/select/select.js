/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
window.credentials = window.credentials || {'dialog': null, 'body': null};
window.credentials.init = function () {
    if (!(window.credentials.dialog)) {
        var div = document.createElement("DIV");
        document.body.appendChild(div);
        div.innerHTML = "<div id='credentialsDialog'><div class='bd'></div></div>";
        window.credentials.body = document.getElementById('credentialsDialog');
    }
};
window.credentials.add = function (e) {
    window.credentials.init();
    fetch(e, {
        method: 'GET',
        headers: crumb.wrap({}),
    }).then(rsp => {
        if (rsp.ok) {
            rsp.text().then((responseText) => {
                // do not apply behaviour on parsed HTML, dialog.form does that later
                // otherwise we have crumb and json fields twice
                window.credentials.body.innerHTML = responseText;
                window.credentials.form = document.getElementById('credentials-dialog-form');
				const data = window.credentials.form.dataset;
				const options = {'title': data['title'], 'okText': data['add'], 'submitButton':false, 'minWidth': '75vw'};
				dialog.form(window.credentials.form, options)
					.then(window.credentials.addSubmit);
				window.credentials.form.querySelector('select').focus();
            });
        }
    });
    return false;
};
window.credentials.refreshAll = function () {
    document.querySelectorAll('select.credentials-select').forEach(function (e) {
        var deps = [];

        function h() {
            var params = {};
            deps.forEach(function (d) {
                params[d.name] = controlValue(d.control);
            });
            var value = e.value;
            updateListBox(e, e.getAttribute("fillUrl"), {
                parameters: params,
                onSuccess: function () {
                    if (value == "") {
                        // reflect the initial value. if the control depends on several other SELECT.select,
                        // it may take several updates before we get the right items, which is why all these precautions.
                        var v = e.getAttribute("value");
                        if (v) {
                            e.value = v;
                            if (e.value == v) {
                                e.removeAttribute("value");
                            } // we were able to apply our initial value
                        }
                    }

                    // if the update changed the current selection, others listening to this control needs to be notified.
                    if (e.value != value) {
                        fireEvent(e, "change");
                    }
                }
            });
        }

        var v = e.getAttribute("fillDependsOn");
        if (v != null) {
            v.split(" ").forEach(function (name) {
                var c = findNearBy(e, name);
                if (c == null) {
                    if (window.console != null) {
                        console.warn("Unable to find nearby " + name);
                    }
                    return;
                }
                deps.push({name: Path.tail(name), control: c});
            });
        }
        h();
    });
};
window.credentials.addSubmit = function (_) {
    const form = window.credentials.form;
    // temporarily attach to DOM (avoid https://github.com/HtmlUnit/htmlunit/issues/740)
    document.body.appendChild(form);
    buildFormTree(form);
    ajaxFormSubmit(form);
    form.remove();

    function ajaxFormSubmit(form) {
        fetch(form.action, {
            method: form.method,
            headers: crumb.wrap({}),
            body: new FormData(form)
        })
            .then(res => res.json())
            .then(data => {
                window.notificationBar.show(data.message, window.notificationBar[data.notificationType]);
                window.credentials.refreshAll();
            })
            .catch((e) => {
                // notificationBar.show(...) with logging ID could be handy here?
                console.error("Could not add credentials:", e);
            })
    }
};

Behaviour.specify("[data-type='credentials-add-store-item']", 'credentials-add-store-item', -99, function(e) {
    e.addEventListener("click", function (event) {
        window.credentials.add(event.target.dataset.url);
    });
    e = null;
});
Behaviour.specify("BUTTON.credentials-add", 'credentials-select', 0, function (e) {
    e.addEventListener("click", window.credentials.add);
    e = null; // avoid memory leak
});
Behaviour.specify("DIV.credentials-select-control", 'credentials-select', 100, function (d) {
    var buttons = Array.from(d.querySelectorAll("INPUT.credentials-select-radio-control"));
    var u = (function () {
        for (var i = 0; i < this.length; i++) {
            this[i]();
        }
    }).bind(buttons.map(function (x) {
                return (function () {
                    if (x.checked) {
                        this.classList.add('credentials-select-content-active');
                        this.classList.remove('credentials-select-content-inactive');
                        this.removeAttribute('field-disabled');
                    } else if (this instanceof HTMLElement) {
                        this.classList.add('credentials-select-content-inactive');
                        this.classList.remove('credentials-select-content-active');
                        this.setAttribute('field-disabled', 'true');
                    }
                }).bind(d.querySelector(x.value == 'select'
                                ? "DIV.credentials-select-content-select"
                                : "DIV.credentials-select-content-param"));
            }));
    u();
    for (var i = 0; i < buttons.length; i++) {
        buttons[i].onclick = buttons[i].onchange = u;
    }
    d = null;
    buttons = null;
    u = null;
});
Behaviour.specify("INPUT.credentials-select", 'credentials-select', -100, function (x) {
  x.onchange = x.oninput = x.onkeyup = (function() {
    if (!this.value.startsWith('${')) {
      this.nextElementSibling.style.display = '';
    } else if (this.value=='' || this.value=='${}' || this.value.indexOf('}')!=this.value.length-1) {
      this.nextElementSibling.style.display = '';
    } else {
      this.nextElementSibling.style.display = 'none';
    }
  }).bind(x);
  x.onchange();
});
Behaviour.specify("DIV.include-user-credentials", 'include-user-credentials', 0, function (e) {
    e.querySelector("input[name='includeUser']").onclick = function (evt) {
        var caution = e.querySelector('div.user-credentials-caution');
        caution.hidden = !this.checked;
    };
    // simpler version of f:helpLink using inline help text
    e.querySelector('span.help-btn').onclick = function (evt) {
        var help = e.querySelector('.help-content');
        help.hidden = !help.hidden;
    };
});
window.setTimeout(function() {
    // HACK: can be removed once base version of Jenkins has fix of https://issues.jenkins-ci.org/browse/JENKINS-26578
    // need to apply the new behaviours to existing objects
    var controls = document.getElementsByClassName('credentials-select-control');
    var count = controls.length;
    for (var i = 0; i < count; i++) {
        Behaviour.applySubtree(controls[i], true);
    }
},1);
