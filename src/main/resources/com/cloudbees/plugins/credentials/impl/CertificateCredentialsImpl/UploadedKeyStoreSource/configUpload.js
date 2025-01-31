// multiple objects named "password" in the form =>
// extend findNearBy to allow selecting by id
if (!findNearBy.patched) {
  var oldNearBy = findNearBy;
  findNearBy = function(el, name) {
    return name.charAt(0) == '#' ? document.querySelector(name.split('/')[0]) : oldNearBy(el, name);
  }
  findNearBy.patched = true;
}

Behaviour.specify(".certificate-file-upload", 'certificate-file-upload', -99, function(uploadedCertFileInput) {
  // Adding a onChange method on the file input to retrieve the value of the file content in a variable
  var fileId = uploadedCertFileInput.id;
  var _onchange = uploadedCertFileInput.onchange;
  if (typeof _onchange === "function") {
    uploadedCertFileInput.onchange = function() { fileOnChange(this); _onchange.call(this); }
  } else {
    uploadedCertFileInput.onchange = fileOnChange.bind(uploadedCertFileInput);
  }
  const base64field = uploadedCertFileInput.closest('.radioBlock-container').querySelector('[name="certificateBase64"]');
  function fileOnChange() {
    // only trigger validation if the PKCS12 upload is selected
    if (uploadedCertFileInput.closest(".form-container").className.indexOf("-hidden") != -1) {
      return
    }
    try { // inspired by https://stackoverflow.com/a/754398
      var uploadedCertFileInputFile = uploadedCertFileInput.files[0];
      var reader = new FileReader();
      reader.onload = function (evt) {
        base64field.value = btoa(evt.target.result);
        var uploadedKeystore = document.getElementById(fileId + "-textbox");
        uploadedKeystore.onchange(uploadedKeystore);
      }
      reader.onerror = function (evt) {
        if (window.console !== null) {
          console.warn("Error during loading uploadedCertFile content", evt);
        }
        uploadedCertFile[fileId] = '';
      }

      reader.readAsBinaryString(uploadedCertFileInputFile);
    }
    catch(e){
      if (window.console !== null) {
        console.warn("Unable to retrieve uploadedCertFile content");
      }
    }
  }

  // workaround for JENKINS-65616
  // tweaks `checkDependsOn` to reference password by id instead of RelativePath
  var r = window.document.getElementById(fileId + "-textbox");
  var p = findNextFormItem(r, 'password');
  if (p) {
    const dependsOn = r.getAttribute('checkDependsOn');
    if (!dependsOn.includes(p.id)) {
      r.setAttribute('checkDependsOn', dependsOn + ' #' + p.id + "/password");
    }
  }
});

