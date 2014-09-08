Ext.define('Mvp.gui.custom.DownloadBasket', {
    extend: 'Mvp.gui.FacetedGridView',
    requires: [
        'Mvp.gui.FilterView',
        'Mvp.gui.GridView',
        'Mvpd.view.DownloadWindowContainer',
        'Ext.button.Button'
    ],

    /**
    * @cfg {Number} pagesize
    * The number of items to have on one page in the grid display.
    */

    // Public methods

    // Private Methods

    constructor: function (config) {
        config.title = false;
        config.header = false;
        this.callParent(arguments);
    },

    createExtraItems: function (config) {
        var me = this;

        var extraItems = [];

        // Export button.
        var exportButton = this.grid.getExportButton();
        var removeButton = Ext.create('Ext.button.Button', {
            text: 'Remove',
            tooltip: 'Remove Selected Items',
            scope: this,
            shadow: true,
            style: 'border: 1px solid #000000;', // The 'border' config has no effect, overriding the toolbar button style is a pain
            margin: '0 1 0 1',
            handler: this.removePressed
        });
        this.downloadButton = Ext.create('Ext.button.Button', {
            text: 'Download',
            tooltip: 'Download Selected Items',
            scope: this,
            shadow: true,
            style: 'border: 1px solid #000000;',
            margin: '0 1 0 1',
            handler: this.downloadPressed
        });
        extraItems.push(removeButton, this.downloadButton, exportButton);

        return extraItems;
    },

    removePressed: function () {
        var store = this.controller.store;
        var records = store.getSelectedRecords();
        var len = records.length;
        if (!len) return;

        ids = {};
        for (var r in records) {
            var id = records[r].get('obsID');
            ids[id] = false;
        }
        store.remove(records);
        store.origTotalCount -= len;
        store.backingStore.remove(records);
        store.backingStore.origTotalCount -= len;
        store.load();
        if (!store.data.length) store.clearFilter();
        store.addHistograms();

        store.each(function (record) {
            var id = record.get('obsID') || records[r].get('obsID');
            var present = ids[id];
            if (present === false) ids[id] = true;
        }, this);

        var bucket = window.DownloadBasket['CAOM'];
        for (var i in ids) {
            if (ids[i] === false) {
                bucket = bucket.replace(i, '').replace(/ ,/, '');
            }
        }

        window.DownloadBasket['CAOM'] = bucket;
        this.controller.update(this.controller.lastUpdateObject);
    },

    downloadPressed: function () {
        // Replace title characters that could be problematic in a filename, then remove duplicate underscores.
        var title = this.controller.getTitle();

        // Specify the file type and name.
        var filetype = 'zip';
        var filename = Mvp.util.Util.filenameCreator(title, filetype);

        // Present the user dialog so they can select a file type and modify the file name.
        var downloadWindow = Ext.create('Mvpd.view.DownloadWindowContainer', {
            controller: this.controller
        });
        downloadWindow.setFilename(filename);
        downloadWindow.setFiletype(filetype);
        downloadWindow.show();
    }

});