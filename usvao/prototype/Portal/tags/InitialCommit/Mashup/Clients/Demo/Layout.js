
Ext.define('DemoApp.Layout', {
    extend: 'Ext.panel.Panel',
    
    statics: {},
    
    constructor: function(config) {
        var me = this;
        
        // Get variables from config
        me.sourceGrid = config.sourceGrid;
        delete config.sourceGrid;
        
        // Apply mandatory config items.       
        Ext.apply(config, {
            //title: 'Border Layout',
            layout: 'border',
            items: [{
                id: 'tlNorthContainer',
                //title: 'North Region',
                region: 'north',     // position for region
                xtype: 'panel',
                layout: 'fit',
                height: 170
                //margins: '0 0 0 0'
            },{
                id: 'tlSouthContainer',
                //title: 'Data',
                region: 'south',     // position for region
                xtype: 'tabpanel',
                height: 215,
                split: true         // enable resizing
            },
            //{
            //    id: 'tlEastContainer',
            //    title: 'East Region',
            //    region:'east',
            //    xtype: 'panel',
            //    width: 100,
            //    collapsible: true,   // make collapsible
            //    layout: 'fit'
            //},
            {
                id: 'tlWestContainer',
                title: 'Filters',
                region:'west',
                xtype: 'panel',
                width: 300,
                split: true,
                collapsible: true,   // make collapsible
                autoScroll: true,
                margins: '0 10 0 0'
            },{
                id: 'tlCenterContainer',
                //title: 'Initial Search Results',
                region: 'center',     // center region is required, no width/height specified
                xtype: 'tabpanel'
            }]
        });
        
        // Apply defaults for config.       
        Ext.applyIf(config, {
            width: 1100,
            height: 860
        });
        
        this.callParent([config]);
        
        // This panel with a border layout has been initialized, so we should be able to get the regional containers.
        this.northPanel = Ext.getCmp('tlNorthContainer');
        this.southPanel = Ext.getCmp('tlSouthContainer');
        this.eastPanel = Ext.getCmp('tlEastContainer');
        this.westPanel = Ext.getCmp('tlWestContainer');
        this.centerPanel = Ext.getCmp('tlCenterContainer');
        

    }
});