/*
 * File: app/view/FacetChooserContainer.js
 * Date: Wed Oct 19 2011 08:45:33 GMT-0400 (Eastern Daylight Time)
 *
 * This file was generated by Ext Designer version 1.2.0.
 * http://www.sencha.com/products/designer/
 *
 * This file will be generated the first time you export.
 *
 * You should implement event handling and custom methods in this
 * class.
 */

Ext.define('Mvpc.view.FacetChooserContainer', {
    extend: 'Mvpc.view.ui.FacetChooserContainer',

    initComponent: function () {
        var me = this;
        me.callParent(arguments);
    },

    constructor: function (config) {
        this.callParent(arguments);
        this.selectedFacets = config.selectedFacets;
        this.defaultFacets = config.defaultFacets;
        this.allFacets = config.allFacets,
        this.decimalFacets = config.decimalFacets;
        this.selectedDecimalFacets = config.selectedDecimalFacets || [];
        this.defaultDecimalFacets = config.defaultDecimalFacets || [];
        this.niceColumnNames = config.niceColumnNames;
        var dockPanel = this.getComponent('dockPanel'),
            toolbar = dockPanel.getComponent('toolbar'),
            content = dockPanel.getComponent('contentContainer');
        toolbar.getComponent('applyButton').addListener('click', this.apply, this);
        toolbar.getComponent('defaultsButton').addListener('click', this.select, this, { selection: this.defaultFacets, numericSelection: this.defaultDecimalFacets });
        toolbar.getComponent('allButton').addListener('click', this.select, this, { selection: this.allFacets, numericSelection: this.decimalFacets });
        toolbar.getComponent('noneButton').addListener('click', this.select, this, { selection: [], numericSelection: [] });

        for (var i in this.allFacets) {
            if (this.allFacets[i].count < 2) continue;  // not facetable
            var key = this.allFacets[i].column;
            var j = this.niceColumnNames.length;
            var nameFound = false;
            while (j--) {
                var c = this.niceColumnNames[j];
                if (c.column == key) {
                    label = c.niceName;
                    nameFound = true;
                    break;
                }
            }
            if (!nameFound) label = key;
            var cb = Ext.create('Ext.form.Checkbox', {
                xtype: 'checkboxField',
                width: 135,
                boxLabel: label,
                itemId: key
            });
            var j = this.selectedFacets.length;
            while (j--) {
                if (key == this.selectedFacets[j].column) {
                    cb.setValue(true);
                    break;
                }
            }
            var count = {
                xtype: 'label',
                width: 50,
                text: this.allFacets[i].count,
                itemId: key + 'Count'
            }
            var c = Ext.create('Ext.container.Container', {
                items: [cb, count],
                layout: 'hbox',
                itemId: key + 'Container'
            });
            content.add(c);
        }

        for (var i in this.decimalFacets) {
            if (this.decimalFacets[i].count < 2) continue;  // not facetable
            var key = this.decimalFacets[i].column;
            var label = key;
            var j = this.niceColumnNames.length;
            while (j--) {
                var c = this.niceColumnNames[j];
                if (c.column == key) {
                    label = c.niceName;
                    break;
                }
            }
            var cb = Ext.create('Ext.form.Checkbox', {
                xtype: 'checkboxField',
                width: 135,
                boxLabel: label,
                itemId: key
            });
            var j = this.selectedDecimalFacets.length;
            while (j--) {
                if (key == this.selectedDecimalFacets[j].column) {
                    cb.setValue(true);
                    break;
                }
            }
            var count = {
                xtype: 'label',
                width: 50,
                text: this.decimalFacets[i].count,
                itemId: key + 'Count'
            }
            var c = Ext.create('Ext.container.Container', {
                items: [cb, count],
                layout: 'hbox',
                itemId: key + 'Container'
            });
            content.add(c);
        }
    },

    select: function (caller, event, config) {
        var selection = config.selection;
        var container = this.getComponent('dockPanel').getComponent('contentContainer');
        var temp = this.allFacets.concat(this.decimalFacets);
        var i = temp.length;
        while (i--) {
            if (temp[i].count < 2) continue;  // not facetable
            var val = temp[i].column;
            container.getComponent(val + 'Container').getComponent(val).setValue(false);
        }
        temp = config.selection.concat(config.numericSelection);
        i = temp.length;
        while (i--) {
            if (temp[i].count < 2) continue;  // not facetable
            var val = temp[i].column;
            container.getComponent(val + 'Container').getComponent(val).setValue(true);
        }
    },

    apply: function () {
        var selection = [],
            numericSelection = [],
        container = this.getComponent('dockPanel').getComponent('contentContainer'),
            warning = false;
        for (var i in this.allFacets) {
            count = this.allFacets[i].count
            if (count < 2) continue;  // not facetable
            var val = this.allFacets[i].column;
            var item = container.getComponent(val + 'Container').getComponent(val);
            if (item.checked) {
                selection.push(this.allFacets[i]);
                if (count > 100) warning = true;
            }
        }
        for (var i in this.decimalFacets) {
            count = this.decimalFacets[i].count
            if (count < 2) continue;  // not facetable
            var val = this.decimalFacets[i].column;
            var item = container.getComponent(val + 'Container').getComponent(val);
            if (item.checked) {
                numericSelection.push(this.decimalFacets[i]);
            }
        }
        var proceed = true;
        if (warning) proceed = confirm("Are you sure you want to filter a text facet with more than 100 possible values?");
        if (proceed) {
            this.selectedFacets = selection;
            this.fireEvent('filtersChanged', { newFacets: this.selectedFacets, newNumericFacets: numericSelection });      // fire event to make whatever called this refresh
        }
    }
});