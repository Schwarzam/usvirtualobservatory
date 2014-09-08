﻿using System;
using tapLib.Args;
using tapLib.Args.ParamQuery;
using tapLib.Db.ParamQuery;
using System.Collections.Generic;

namespace tapLib.Stsci {
    public class RegistryGenerator : AbstractSqlQueryGenerator {
        /// <summary>
        /// {0} SELECT plus the list of query columns needed (from default generator)
        /// {1} ra in degrees
        /// {2} dec in degrees
        /// {3} query level - assumes all of HLA tables
        /// {4} radius of search in minutes
        /// {5} is the additional where business from the query
        /// </summary>

        const string ACTIVE_RESOURCE_TEMPLATE = "SELECT {0} FROM RESOURCE WHERE [@STATUS] = 1 AND {1}";
        const string WITHOUT_WHERE_ENDING = "AND ";

        private String _selectClause;
        private String _whereClause;

        public override string tableName { get { return "resource"; } }

        public override string generateSelectArg(QueryArg qa) {
            string database = Config.TapConfiguration.Instance.DatabaseForTable(qa.tableName);

            String baseResult = base.generateSelectArg(qa);
            if (!qa.selectFields.Contains("$STD") && !qa.selectFields.Contains("$ALL"))
            {
                return baseResult;
            }
            else
            {
                List<string> list = null;
                string strReplace = string.Empty;
                if (qa.selectFields.Contains("$STD"))
                {
                    list = Config.TapConfiguration.Instance.StdColumns(database, qa.tableName);
                    strReplace = "$STD";
                }
                else //$ALL
                {
                    list = Config.TapConfiguration.Instance.AllColumns(database, qa.tableName);
                    strReplace = "$ALL";
                }

               string strList = string.Empty;
               for (int i = 0; i < list.Count; ++i)
               {
                   strList += list[i];
                   if (i < list.Count - 1)
                       strList += ", ";
               }
                
               return baseResult.Replace(strReplace, strList);
            }
        }

        public bool CheckTableValidity(TapQueryArgs args)
        {
            if ( args.query.from.StartsWith("$TAP_SCHEMA")  )
            {
                //do something
                return true;
            }
            else 
            {
                string database = Config.TapConfiguration.Instance.DatabaseForTable(args.query.tableName);

                //do we serve this table at all?
                if( !Config.TapConfiguration.Instance.isSupportedTable(database, args.query.from))
                return false;

                //are "select" columns relevant?
                List<string> cols = Config.TapConfiguration.Instance.AllColumns(database, args.query.from);
                if (cols.Count == 0)
                {
                    args.query._AddProblem("No valid columns to select.");
                    return false;
                }

                    for( int i = 0; i < args.query.selectFields.Count; ++i )
                    {
                        string str = args.query.selectFields[i];
                        if (str != "$ALL" && str != "$POS" && str != "$STD" &&
                            !cols.Contains(str))
                        {
                            args.query._AddProblem("invalid select column: " + str + '.');
                            return false;
                        }
                    }
            }
            return true;
        }

        public override bool generateSQL(TapQueryArgs args)
        {
            if (args == null) return false;
            if (!CheckTableValidity(args)) return false;

            if (!base.generateSQL(args)) return false;

            _selectClause = generateSelectArg(args.query);

            _whereClause = generateWhereArg(args.query); 

            return true;
        }

        public override string ToSQL(TapPos pos, TapSizeArg size, TapRegionArg region, TapMTimeArg mtime)
        {
            return this.ToSQL();               
        }

        // Other ToSQL is base class version
        public override string ToSQL()
        {
            string formatted = String.Format(ACTIVE_RESOURCE_TEMPLATE,
                     _selectClause,
                     _whereClause);
            if (_whereClause == string.Empty)
                return formatted.Substring(0, formatted.LastIndexOf(WITHOUT_WHERE_ENDING));

            return formatted;
        }
    }
}