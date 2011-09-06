package org.mmx.xdtl.runtime.command;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.mmx.xdtl.model.Command;
import org.mmx.xdtl.model.Parameter;
import org.mmx.xdtl.model.Variable;
import org.mmx.xdtl.model.XdtlException;
import org.mmx.xdtl.model.command.Render;
import org.mmx.xdtl.runtime.Context;
import org.mmx.xdtl.runtime.ExpressionEvaluator;
import org.mmx.xdtl.runtime.RuntimeCommand;
import org.mmx.xdtl.runtime.TypeConverter;

import com.google.inject.Inject;

public class RenderCmdBuilder extends AbstractCmdBuilder {
    private final ExpressionEvaluator m_exprEvaluator;
    private final TypeConverter m_typeConverter;

    private Mappings m_source;
    private String m_template;
    private String m_target;
    private Object m_rowset;
    private ArrayList<Variable> m_parameterList;
    
    @Inject
    public RenderCmdBuilder(ExpressionEvaluator exprEvaluator,
            TypeConverter typeConverter) {
        m_exprEvaluator = exprEvaluator;
        m_typeConverter = typeConverter;
    }
    
    @Override
    protected RuntimeCommand createInstance() throws Exception {
        Constructor<? extends RuntimeCommand> ctor =
            getRuntimeClass().getConstructor(String.class, Mappings.class,
                    String.class, Object.class, List.class);
        
        return ctor.newInstance(m_template, m_source, m_target, m_rowset, m_parameterList);
    }

    @Override
    protected void evaluate(Command cmd) throws Exception {
        Render render = (Render) cmd;
        Context ctx = getContext();
        
        m_template = m_typeConverter.toString(m_exprEvaluator.evaluate(ctx,
                render.getTemplateUrl()));
        
        Object source = m_exprEvaluator.evaluate(ctx, emptyStrToNull(
                render.getSource()));
        
        if (source != null && !(source instanceof Mappings)) {
            throw new XdtlException("Invalid variable type: render source must be of '" +
                    Mappings.class + "', found '" + source.getClass() + "' instead",
                    cmd.getSourceLocator());
        }
        
        m_source = (Mappings) source;
        m_target = m_typeConverter.toString(m_exprEvaluator.evaluate(ctx,
                render.getTarget()));
        m_rowset = m_exprEvaluator.evaluate(ctx, emptyStrToNull(
                render.getRowset()));
        
        evaluateParameters(ctx, render.getParameterList());
    }

    private void evaluateParameters(Context ctx, List<Parameter> params) {
        HashSet<String> parameterNames = new HashSet<String>();
        m_parameterList = new ArrayList<Variable>(params.size());
        
        for (Parameter param: params) {
            String name = m_typeConverter.toString(m_exprEvaluator.evaluate(ctx, param.getName()));
            if (!parameterNames.add(name)) {
                throw new XdtlException(String.format("Duplicate parameter name '%s'", name),
                        param.getSourceLocator());
            }
            
            Object value = m_exprEvaluator.evaluate(ctx, param.getValue());
            m_parameterList.add(new Variable(name, value));
        }
    }
    
    private static String emptyStrToNull(String str) {
        return (str != null && str.length() == 0) ? null : str;
    }
}
