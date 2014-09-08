Ext.define('Mvp.util.Uploader', {
    requires: ['Mvpd.view.UploadOptionsForm'],
    statics: {
        showDialog: function(portalScope) {
             var uploadOptionsForm = Ext.create('Mvpd.view.UploadOptionsForm', {
                portalScope: portalScope});
             uploadOptionsForm.show();
        },
        
        uploadFile: function (fileForm, successCallback, cbScope) {
                fileForm.submit({
                    url: '../../Mashup.asmx/upload',
                    waitMsg: 'Uploading your file...',
                    scope: cbScope,
                    success: successCallback,
                    failure: function (f, action) {
                        Ext.Msg.alert("returned failure");
                    }
            });
        }

    },
    
    constructor: function(config) {
        Ext.apply(this, config);

    }
    
    
 

});
    