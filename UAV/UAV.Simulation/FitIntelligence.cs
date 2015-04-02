﻿using System;
using System.Linq;
using UAV.Prediction;
using UAV.Common;

namespace UAV.Simulation
{
    public class FitIntelligence : IIntelligence
    {
        public FitIntelligence()
        {
        }

        public int FitFunctionDegree { get; set; }
        public int HistoryLength { get; set; }

        #region IIntelligence implementation

        public Vector2D ComputeCommand(WorldState worldState, Vector2D baseCommand)
        {
            Func<double, double> funcX = 
                Fitters.GeneratePolynomialFit(
                    new UAV.Common.Range(0, HistoryLength, 1),
                    (from l in worldState.LocationHistory.TakeLast(HistoryLength)
                        select l.Item2.X).ToList(),
                    FitFunctionDegree
                );

            Func<double, double> funcY = 
                Fitters.GeneratePolynomialFit(
                    new UAV.Common.Range(0, HistoryLength, 1),
                    (from l in worldState.LocationHistory.TakeLast(HistoryLength)
                        select l.Item2.Y).ToList(),
                    FitFunctionDegree
                );

            // Predict next value and return.
            double time = HistoryLength + 1;
            var ret = new Vector2D(funcX(time), funcY(time));
            return ret;
        }

        #endregion
    }
}
