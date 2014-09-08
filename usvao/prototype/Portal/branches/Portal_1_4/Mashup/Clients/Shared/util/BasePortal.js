
Ext.define('Mvp.util.BasePortal', {
    requires: ['Mvp.search.SearchContext', 'Mvp.gui.PortalBorderContainer', 'Mvp.app.App', 'Mvp.util.Constants'],

    constructor: function (config) {
        Ext.apply(this, config);

        app = this.app = Ext.create('Mvp.app.App', {});
        var em = this.app.getEventManager();
        em.addListener('APP.download.view', this.viewDownloadBasket, this);

        if (AppConfig.useAV) {
            var avRenderType = AppConfig.avRenderType || 'flash';
            var avPlacement = AppConfig.avPlacement || 'east';
            this.avPanel = Ext.create('Mvpc.view.AstroViewContainer', {
                width: Mvp.util.Constants.ASTROVIEW_DEFAULT_WIDTH,
                rendertype: avRenderType,
                region: avPlacement,
                split: true,
                collapsible: true,
                animCollapse: false,
                app: this.app
            });
        }
    },

    // Public methods

    // Private methods
    run: function () {
        Ext.log({
            msg: 'BasePortal.run() needs to be overridden.',
            level: 'error'
        })
    },

    searchCallback: function (searchInput, searchParams) {
        // searchInput *must* contain inputText.
        // It may optionally contain title and description, which will default to
        // inputText if not present.
        Ext.log('searchParams = ' + Ext.encode(searchParams));

        // TBD:  Create result view(s) (e.g., GridResultView, FilterResultView, AlbumView..)
        // These may need to know there destination containers or otherwise how
        // to add their contents to something.
        var searchContext = Ext.create('Mvp.search.SearchContext', {
            searchInput: searchInput,
            searchParams: searchParams,
            useAv: AppConfig.useAV
        });
        searchContext.addListener('newsearch', this.searchCallback, this);

        var sp = Mvp.search.SearchParams;
        var resultType = sp.resultTypes[searchParams.result.type];
        var viewClass = resultType.defaultView.type;
        var viewConfig = Ext.clone(resultType.defaultView.config);
        var title = searchContext.getTitle();
        var description = searchContext.getDescription();
        Ext.apply(viewConfig, {
            title: title,
            description: description,
            controller: searchContext,
            closable: !searchParams.windowed
        });
		if (searchParams.whatIsWindow) {
			Ext.apply(viewConfig, {
				header: false
			});
		}
        var view = Ext.create(viewClass, viewConfig);
        if (searchParams.windowed) {
            if (this.downloadBasketWindow == undefined) this.downloadBasketWindow = Ext.create('Ext.window.Window', {
                closeAction: 'hide',
                layout: 'fit',
                constrainHeader: true,
                title: title,
                height: 600, width: 800,
                collapsible: true
            });
            this.downloadBasketWindow.removeAll();
            this.downloadBasketWindow.add(view);
            this.downloadBasketWindow.show();
        } else if (searchParams.whatIsWindow) {
            if (this.whatIsWindow == undefined) this.whatIsWindow = Ext.create('Ext.window.Window', {
                closeAction: 'hide',
                layout: 'fit',
                constrainHeader: true,
                title: title,
                height: 210, width: 670, x:0, y: 0,
                collapsible: true
            });
            this.whatIsWindow.removeAll();
            this.whatIsWindow.add(view);
            this.whatIsWindow.show();
        } else if (view.newWindow) {
            var w = Ext.create('Ext.window.Window', {
                closeAction: 'hide',
                layout: 'fit',
                constrainHeader: true,
                title: title,
                height: 600, width: 800,
                collapsible: true
            });
            w.removeAll();
            w.add(view);
            w.show();
        } else {
            this.resultPanel.add(view);
            this.resultPanel.setActiveTab(view);
        }
        view.on('beforeclose', this.onBeforeClose, this, { context: searchContext });
        if (Ext.isFunction(view.setStarted)) {
            view.setStarted();
        }


        if (searchParams.resolve) {
            // TBD:  Update GUI to show name resolver in progress if necessary.
            // This could just be handled by the above views.
        }

        this.app.addContext(searchContext);

        searchContext.start();

    },

    onBeforeClose: function (el, eOpts) {
        this.app.removeContext(eOpts.context);
    },

    resolveCallback: function (nameResolverStore) {
        // Now we know the resolver is complete.

        //  TBD: Update the GUI if necessary.
    },

    onTabChange: function (tabPanel, newCard, oldCard, eOpts) {
        var newContext = newCard.controller;
        if (newContext) this.app.setActiveContext(newContext);
    },

    viewDownloadBasket: function (config) {
        if (this.downloadBasketWindow) this.downloadBasketWindow.show();
    }

});