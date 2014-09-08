/*
 * File: app/view/ui/FacetChooserContainer.js
 * Date: Tue Apr 03 2012 10:00:14 GMT-0400 (Eastern Daylight Time)
 *
 * This file was generated by Ext Designer version 1.2.2.
 * http://www.sencha.com/products/designer/
 *
 * This file will be auto-generated each and everytime you export.
 *
 * Do NOT hand edit this file.
 */

Ext.define('Mvpc.view.ui.FacetChooserContainer', {
    extend: 'Ext.panel.Panel',

    itemId: 'dockPanel',
    autoScroll: true,
    layout: {
        type: 'fit'
    },

    initComponent: function() {
        var me = this;

        Ext.applyIf(me, {
            dockedItems: [
                {
                    xtype: 'toolbar',
                    itemId: 'toolbar',
                    dock: 'top',
                    items: [
                        {
                            xtype: 'button',
                            itemId: 'applyButton',
                            text: 'Apply'
                        },
                        {
                            xtype: 'button',
                            itemId: 'defaultsButton',
                            text: 'Defaults'
                        },
                        {
                            xtype: 'button',
                            itemId: 'allButton',
                            text: 'All'
                        },
                        {
                            xtype: 'button',
                            itemId: 'noneButton',
                            text: 'None'
                        }
                    ]
                }
            ],
            items: [
                {
                    xtype: 'container',
                    itemId: 'contentContainer',
                    autoScroll: true
                }
            ]
        });

        me.callParent(arguments);
    }
});