Ext.define('Mvp.util.Util', {
    requires: ['Mvp.data.Histogram', 'Mvp.data.DecimalHistogram'],
    statics: {
        createLink: function (link, text, onclick) {
            var linkText = (text) ? text : link;
            var htmlLink = '<a href="' + link + '" ' + 
            ((onclick) ? 'onclick="' + onclick + '" ' : 'target="_blank" ')
            + '>' + linkText + '</a>';
            return htmlLink;
        },

        createLinkIf: function (link, text) {
            var retVal = link;
            if (Mvp.util.Util.isUrl(link)) {
                retVal = Mvp.util.Util.createLink(link, text);
            }
            return retVal;
        },

        createImageLink: function (link, imageSrc, title, width, height, record) {
            var src = (!record || !record.imageError) ? imageSrc : '../Shared/img/nopreview.png';

            var linkTitle = (title) ? title : link;
            var html = '<a href="' + link + '" target="_blank" title="' + linkTitle + '">' +
                Mvp.util.Util.createImageHtml(src, linkTitle, width, height, record) + '</a>';
            return html;
        },

        createImageHtml: function (imageSrc, title, width, height, record) {
            var src = (!record || !record.imageError) ? imageSrc : '../Shared/img/nopreview.png';
            var img = new Image(width, height);
            img.onerror = function () {
                this.src = '../Shared/img/nopreview.png';
                if (record) record.imageError = true;   // hangs a flag on the record that the image is bad, if a record was passed in
            }
            img.src = src;


            var alt = (title) ? title : '';
            var html = '<img src="' + src +
            ((width) ? ('" width="' + width) : '') +
            ((height) ? ('" height="' + height) : '') +
            '" alt="' + alt + '" title="' + title + '" onerror="this.src=\'../Shared/img/nopreview.png\';" />';
            return html;
        },

        isUrl: function (url) {
            isUrl = false;
            if (Ext.isString(url)) {
                isUrl = url.match('^(http|ftp)s?:\/\/');
            }
            return isUrl;
        },

        isFtpUrl: function (url) {
            isUrl = false;
            if (Ext.isString(url)) {
                isUrl = url.match('^ftps?:\/\/');
            }
            return isUrl;
        },
        
        // The accessUrl value for registered VO services needs to be patched a little in some cases.
        // First, "&amp;" needs to be replaced with "&".  Then the URL needs to end with either a "?" or "&"
        // so that it's ready for the additional query parameters.
        fixAccessUrl: function(accessUrl) {
            var fixed = accessUrl;
            
            if (fixed) {
                fixed = fixed.replace(/amp;/gi, '');
    
                if (!fixed.match(/(\?&)$/)) {
                    if (fixed.match(/\?/)) {
                        fixed = fixed + '&';
                    } else {
                        fixed = fixed + '?';
                    }
                }
            }
            return fixed;
        },

        /**
        * This method creates a new object whose attributes are the subset of given object's attributes
        * that start with the given prefix followed by a period.  The new attributes have the prefix and
        * period stripped off.
        * Arguments:
        * object:  The object from which to extract the prefixed attributes
        * prefix:  A string containing the prefix to look for
         
        * 
        * So this line of code:
        * var cc = Mvp.util.Util.extractByPrefix({cc.a": "aval", "cc.b": "bval", "ccc": "cval", "vot.a": "votaval"}, 'cc');
        * would create an object called cc that contained:
        * {a: "aval", b: "bval}    (no cval because the ccc attribute has no period)
        */
        extractByPrefix: function (object, prefix) {
            var extracted = {};
            Ext.Object.each(object, function (key, value, myself) {
                var re = prefix + '\.';
                if (key.match(re)) {
                    var shortName = key.replace(re, '');
                    extracted[shortName] = value;
                }
            });

            return extracted;
        },

        numberValidator: function (val) {
            if (Ext.Number.from(val, -1) >= 0) {
                return true;
            } else {
                return 'Value must be a non-negative number.';
            }
        },

        filenameCreator: function (startingTitle, extension) {
            // drop all non-word characters, then remove all duplicate, trailing and leading underscores
            var title = startingTitle.trim().replace(/\W+/g, '_').replace(/_+/g, '_').replace(/(^_|_$|nbsp)/g, '');

            // Duplicate labels are common, so try and remove them
            var tokens = title.split('_');
            var testToken;
            for (var i = 0; i <= tokens.length; i++) {
                testToken = tokens[i];
                if (testToken) {
                    for (var j = i + 1; j <= tokens.length; j++) {
                        if (tokens[j] && testToken.toLowerCase() == tokens[j].toLowerCase()) {
                            tokens.splice(j, 1);
                        }
                    }
                }
            }
            title = tokens.join('_') + '.' + extension;
            return title;

        },

        decimalHistogram: function (store, property, min, max, nBuckets, ignoreValue) {
            var histogram = new Array(nBuckets);
            for (var i = 0; i < nBuckets; i++) histogram[i] = 0;
            var bucketSize = (max - min) / nBuckets;
            var items = store;
            var nItems = items.length;

            if (items && Ext.isArray(items)) {
                var i = items.length;
                while (i--) {
                    var record = items[i];
                    var value = record.get(property);
                    if ((value < min) || (value > max)) continue;
                    if (ignoreValue != value) {
                        var b = Math.floor((value - min) / bucketSize);
                        b = Math.min(b, nBuckets - 1);
                        histogram[b]++;

                    }
                }
            }
            var hist = [],
            maxCount = 0;
            for (var i = 0; i < nBuckets; i++) {
                var r = histogram[i];
                hist.push({ bucket: min + i * bucketSize, ratio: r, key: min + i * bucketSize, count: r, exclude: 0 });
                if (r > maxCount) maxCount = r;
            }
            return {
                histArray: hist,
                max: maxCount
            };
        },

        decimalHistogramToStore: function (histArray) {
            var s = Ext.create('Ext.data.JsonStore', { extend: 'Mvp.data.DecimalHistogram', fields: ['key', 'count'], data: histArray });
            return s;
        },

        potentialHistogram: function (potentialStore, property, min, max, nBuckets, ignoreValue, minBound, maxBound) {
            //  this function creates a decimal histogram, but has the additional parameters minBound and maxBound that define
            //  the range of values outside of which to mark values as excluded
            var histogram = new Array(nBuckets), exclusionHistogram = new Array(nBuckets);
            for (var i = 0; i < nBuckets; i++) {
                histogram[i] = 0;
                exclusionHistogram[i] = 0
            }
            var bucketSize = (max - min) / nBuckets;

            if (potentialStore && Ext.isArray(potentialStore)) {
                var i = potentialStore.length;
                while (i--) {
                    var record = potentialStore[i];
                    var value = record.get(property) || record[property];
                    if (ignoreValue != value) {
                        var b = Math.floor((value - min) / bucketSize);
                        if ((b < 0) || (b > 99)) continue;
                        (((value < minBound) && (value >= min)) || ((value > maxBound) && (value <= max))) ? exclusionHistogram[b]++ : histogram[b]++;
                    }
                }
            }
            var hist = [],
            maxCount = 0;
            for (var i = 0; i < nBuckets; i++) {
                var inc = histogram[i], ex = exclusionHistogram[i];
                hist.push({ bucket: min + i * bucketSize, ratio: inc, key: min + i * bucketSize, count: inc, excluded: ex });
                if (inc > maxCount) maxCount = inc;
                if (ex > maxCount) maxCount = ex;
            }
            return {
                histArray: hist,
                max: maxCount
            };
        },

        potentialHistogramToStore: function (histArray) {
            var s = Ext.create('Ext.data.JsonStore', { extend: 'Mvp.data.DecimalHistogram', fields: ['key', 'count', 'excluded'], data: histArray });
            return s;
        },

        // This is supposed to generate a histogram of the contents of a column of data
        // in the specified mixed collection.
        histogram: function (mixedCollection, property, separator) {
            var items = mixedCollection;
            var hist = {};
            hist._numEntries = 0;
            if (items && Ext.isArray(items)) {
                var i = items.length;
                while (i--) {
                    var record = items[i];
                    var value = record.get(property);
                    if ((value !== undefined) && (value !== null)) {
                        var stringVal = value.toString();
                        var keys = [stringVal];
                        if (separator) {
                            keys = stringVal.split(separator);
                        }
                        var k = keys.length;
                        while (k--) {
                            var key = keys[k];
                            //if (key != '') {
                            var histEntry = hist[key];
                            if (!histEntry) {
                                hist._numEntries++;
                                hist[key] = { key: key, count: 1 };
                            } else {
                                histEntry.count++;
                            }
                            //}
                        }
                    }
                }
            }
            return hist;
        },

        histogramToArray: function (histogram) {
            var histArray = new Array(histogram._numEntries);
            var i = 0;
            for (property in histogram) {
                if (property.charAt(0) !== '_') {
                    histArray[i++] = histogram[property];
                }
            }
            return histArray;
        },

        histogramArrayToStore: function (histArray) {
            var store = Ext.create('Ext.data.Store', {
                model: 'Mvp.data.Histogram'
            });
            store.add(histArray);
            return store;
        },

        logicalSort: function (a, b) {
            // sorts in ascending alphabetical/numeric order - standard array .sort() sorts lexicographically, which gets numbers wrong
            var na = Number(a), nb = Number(b)
            if ((na == a) && (nb == b)) return na - nb;
            var sa = a.toString().toLowerCase(),
                sb = b.toString().toLowerCase();
            return sa.localeCompare(sb);
        },

        adoptContext: function (sourceWin, destWin) {
            var shareNamespace = function (ns, sourceWin, destWin) {
                if (sourceWin[ns]) {
                    destWin[ns] = sourceWin[ns];
                }
            };
            shareNamespace('Ext', sourceWin, destWin);
            shareNamespace('Mvp', sourceWin, destWin);
            shareNamespace('Mvpc', sourceWin, destWin);
            shareNamespace('Mvpd', sourceWin, destWin);
            shareNamespace('Vao', sourceWin, destWin);
            shareNamespace('Mast', sourceWin, destWin);

            // Make the injection function easier to access.
            if (sourceWin.Vao) {
                Mvp.injectSearchText = Vao.view.VaoTopBar.injectSearchText;
            } else if (sourceWin.Mast) {
                Mvp.injectSearchText = Mast.view.MastTopBar.injectSearchText;
            }
        },

        parseCsvWithDashes: function (str) {    // parses a string of the form "1, 4-6, 9" and returns "1,4,5,6,9"
            var tokens = str.replace(' ', '').replace(/(^,|,$)/, '').split(',');
            var dict = {};
            var retVal = '';
            for (var i in tokens) {
                var token = tokens[i];
                if (token.trim() === '') continue;
                var range = token.split('-');
                var min = Number(range[0]),
                    len = range.length,
                    max = len > 1 ? Number(range[len - 1]) : undefined;
                if (max !== undefined && max < min) {
                    var t = min;
                    min = max;
                    max = t;
                }
                dict[min] = true;
                if (max) {
                    for (var j = min + 1; j <= max; j++) {
                        dict[j] = true;
                    }
                }
            }
            var first = true;
            for (var i in dict) {
                if (!first) retVal += ',';
                retVal += i;
                first = false;
            }
            return retVal;
        }


    }
});