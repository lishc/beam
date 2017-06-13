/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.dsls.sql.interpreter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.beam.dsls.sql.exception.BeamSqlUnsupportedException;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlAndExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlCaseExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlEqualExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlInputRefExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlIsNotNullExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlIsNullExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlLargerThanEqualExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlLargerThanExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlLessThanEqualExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlLessThanExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlNotEqualExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlOrExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlPrimitive;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlReinterpretExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlUdfExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlWindowEndExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlWindowExpression;
import org.apache.beam.dsls.sql.interpreter.operator.BeamSqlWindowStartExpression;
import org.apache.beam.dsls.sql.interpreter.operator.arithmetic.BeamSqlDivideExpression;
import org.apache.beam.dsls.sql.interpreter.operator.arithmetic.BeamSqlMinusExpression;
import org.apache.beam.dsls.sql.interpreter.operator.arithmetic.BeamSqlModExpression;
import org.apache.beam.dsls.sql.interpreter.operator.arithmetic.BeamSqlMultiplyExpression;
import org.apache.beam.dsls.sql.interpreter.operator.arithmetic.BeamSqlPlusExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlCurrentDateExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlCurrentTimeExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlDateCeilExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlDateFloorExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlExtractExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlLocalTimeExpression;
import org.apache.beam.dsls.sql.interpreter.operator.date.BeamSqlLocalTimestampExpression;
import org.apache.beam.dsls.sql.interpreter.operator.math.BeamSqlAbsExpression;
import org.apache.beam.dsls.sql.interpreter.operator.math.BeamSqlRoundExpression;
import org.apache.beam.dsls.sql.interpreter.operator.math.BeamSqlSqrtExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlCharLengthExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlConcatExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlInitCapExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlLowerExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlOverlayExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlPositionExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlSubstringExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlTrimExpression;
import org.apache.beam.dsls.sql.interpreter.operator.string.BeamSqlUpperExpression;
import org.apache.beam.dsls.sql.rel.BeamFilterRel;
import org.apache.beam.dsls.sql.rel.BeamProjectRel;
import org.apache.beam.dsls.sql.rel.BeamRelNode;
import org.apache.beam.dsls.sql.schema.BeamSqlRow;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.apache.calcite.util.NlsString;

/**
 * Executor based on {@link BeamSqlExpression} and {@link BeamSqlPrimitive}.
 * {@code BeamSqlFnExecutor} converts a {@link BeamRelNode} to a {@link BeamSqlExpression},
 * which can be evaluated against the {@link BeamSqlRow}.
 *
 */
public class BeamSqlFnExecutor implements BeamSqlExpressionExecutor {
  protected List<BeamSqlExpression> exps;

  public BeamSqlFnExecutor(BeamRelNode relNode) {
    this.exps = new ArrayList<>();
    if (relNode instanceof BeamFilterRel) {
      BeamFilterRel filterNode = (BeamFilterRel) relNode;
      RexNode condition = filterNode.getCondition();
      exps.add(buildExpression(condition));
    } else if (relNode instanceof BeamProjectRel) {
      BeamProjectRel projectNode = (BeamProjectRel) relNode;
      List<RexNode> projects = projectNode.getProjects();
      for (RexNode rexNode : projects) {
        exps.add(buildExpression(rexNode));
      }
    } else {
      throw new BeamSqlUnsupportedException(
          String.format("%s is not supported yet", relNode.getClass().toString()));
    }
  }

  /**
   * {@link #buildExpression(RexNode)} visits the operands of {@link RexNode} recursively,
   * and represent each {@link SqlOperator} with a corresponding {@link BeamSqlExpression}.
   */
  static BeamSqlExpression buildExpression(RexNode rexNode) {
    if (rexNode instanceof RexLiteral) {
      RexLiteral node = (RexLiteral) rexNode;
      SqlTypeName type = node.getTypeName();
      Object value = node.getValue();

      if (SqlTypeName.CHAR_TYPES.contains(type)
          && node.getValue() instanceof NlsString) {
        // NlsString is not serializable, we need to convert
        // it to string explicitly.
        return BeamSqlPrimitive.of(type, ((NlsString) value).getValue());
      } else if (type == SqlTypeName.DATE && value instanceof Calendar) {
        // does this actually make sense?
        // Calcite actually treat Calendar as the java type of Date Literal
        return BeamSqlPrimitive.of(type, ((Calendar) value).getTime());
      } else {
        return BeamSqlPrimitive.of(type, value);
      }
    } else if (rexNode instanceof RexInputRef) {
      RexInputRef node = (RexInputRef) rexNode;
      return new BeamSqlInputRefExpression(node.getType().getSqlTypeName(), node.getIndex());
    } else if (rexNode instanceof RexCall) {
      RexCall node = (RexCall) rexNode;
      String opName = node.op.getName();
      List<BeamSqlExpression> subExps = new ArrayList<>();
      for (RexNode subNode : node.getOperands()) {
        subExps.add(buildExpression(subNode));
      }
      switch (opName) {
        case "AND":
        return new BeamSqlAndExpression(subExps);
        case "OR":
          return new BeamSqlOrExpression(subExps);

        case "=":
          return new BeamSqlEqualExpression(subExps);
        case "<>=":
          return new BeamSqlNotEqualExpression(subExps);
        case ">":
          return new BeamSqlLargerThanExpression(subExps);
        case ">=":
          return new BeamSqlLargerThanEqualExpression(subExps);
        case "<":
          return new BeamSqlLessThanExpression(subExps);
        case "<=":
          return new BeamSqlLessThanEqualExpression(subExps);

        // arithmetic operators
        case "+":
          return new BeamSqlPlusExpression(subExps);
        case "-":
          return new BeamSqlMinusExpression(subExps);
        case "*":
          return new BeamSqlMultiplyExpression(subExps);
        case "/":
        case "/INT":
          return new BeamSqlDivideExpression(subExps);
        case "MOD":
          return new BeamSqlModExpression(subExps);

        case "ABS":
          return new BeamSqlAbsExpression(subExps);
        case "SQRT":
          return new BeamSqlSqrtExpression(subExps);
        case "ROUND":
          return new BeamSqlRoundExpression(subExps);

        // string operators
        case "||":
          return new BeamSqlConcatExpression(subExps);
        case "POSITION":
          return new BeamSqlPositionExpression(subExps);
        case "CHAR_LENGTH":
        case "CHARACTER_LENGTH":
          return new BeamSqlCharLengthExpression(subExps);
        case "UPPER":
          return new BeamSqlUpperExpression(subExps);
        case "LOWER":
          return new BeamSqlLowerExpression(subExps);
        case "TRIM":
          return new BeamSqlTrimExpression(subExps);
        case "SUBSTRING":
          return new BeamSqlSubstringExpression(subExps);
        case "OVERLAY":
          return new BeamSqlOverlayExpression(subExps);
        case "INITCAP":
          return new BeamSqlInitCapExpression(subExps);

        // date functions
        case "REINTERPRET":
          return new BeamSqlReinterpretExpression(subExps, node.type.getSqlTypeName());
        case "CEIL":
          return new BeamSqlDateCeilExpression(subExps);
        case "FLOOR":
          return new BeamSqlDateFloorExpression(subExps);
        case "EXTRACT_DATE":
        case "EXTRACT":
          return new BeamSqlExtractExpression(subExps);
        case "LOCALTIME":
          return new BeamSqlLocalTimeExpression(subExps);
        case "LOCALTIMESTAMP":
          return new BeamSqlLocalTimestampExpression(subExps);
        case "CURRENT_TIME":
        case "CURRENT_TIMESTAMP":
          return new BeamSqlCurrentTimeExpression();
        case "CURRENT_DATE":
          return new BeamSqlCurrentDateExpression();


        case "CASE":
          return new BeamSqlCaseExpression(subExps);

        case "IS NULL":
          return new BeamSqlIsNullExpression(subExps.get(0));
      case "IS NOT NULL":
        return new BeamSqlIsNotNullExpression(subExps.get(0));

      case "HOP":
      case "TUMBLE":
      case "SESSION":
        return new BeamSqlWindowExpression(subExps, node.type.getSqlTypeName());
      case "HOP_START":
      case "TUMBLE_START":
      case "SESSION_START":
        return new BeamSqlWindowStartExpression();
      case "HOP_END":
      case "TUMBLE_END":
      case "SESSION_END":
        return new BeamSqlWindowEndExpression();
      default:
        //handle UDF
        if (((RexCall) rexNode).getOperator() instanceof SqlUserDefinedFunction) {
          SqlUserDefinedFunction udf = (SqlUserDefinedFunction) ((RexCall) rexNode).getOperator();
          ScalarFunctionImpl fn = (ScalarFunctionImpl) udf.getFunction();
          return new BeamSqlUdfExpression(fn.method, subExps,
              ((RexCall) rexNode).type.getSqlTypeName());
        } else {
          throw new BeamSqlUnsupportedException("Operator: " + opName + " not supported yet!");
        }
      }
    } else {
      throw new BeamSqlUnsupportedException(
          String.format("%s is not supported yet", rexNode.getClass().toString()));
    }
  }

  @Override
  public void prepare() {
  }

  @Override
  public List<Object> execute(BeamSqlRow inputRecord) {
    List<Object> results = new ArrayList<>();
    for (BeamSqlExpression exp : exps) {
      results.add(exp.evaluate(inputRecord).getValue());
    }
    return results;
  }

  @Override
  public void close() {
  }

}