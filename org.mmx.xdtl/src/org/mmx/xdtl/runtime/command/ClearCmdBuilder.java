/**
 * 
 */
package org.mmx.xdtl.runtime.command;

import java.lang.reflect.Constructor;

import org.mmx.xdtl.model.Command;
import org.mmx.xdtl.model.command.Clear;
import org.mmx.xdtl.runtime.Context;
import org.mmx.xdtl.runtime.ExpressionEvaluator;
import org.mmx.xdtl.runtime.RuntimeCommand;
import org.mmx.xdtl.runtime.TypeConverter;

import com.google.inject.Inject;

/**
 * @author vsi
 */
public class ClearCmdBuilder extends AbstractCmdBuilder {
    private static final String CMD_NAME = "clear";
    
    private final ExpressionEvaluator m_exprEval;
    private final TypeConverter m_typeConv;

    private String m_cmd;
    private String m_target;
    
    @Inject
    public ClearCmdBuilder(ExpressionEvaluator exprEvaluator,
            TypeConverter typeConverter) {
        m_exprEval = exprEvaluator;
        m_typeConv = typeConverter;
    }
    
    /**
     * @see org.mmx.xdtl.runtime.command.AbstractCmdBuilder#createInstance()
     */
    @Override
    protected RuntimeCommand createInstance() throws Exception {
        Constructor<? extends RuntimeCommand> ctor =
            getRuntimeClass().getConstructor(String.class, String.class);
        
        return ctor.newInstance(m_cmd, m_target);
    }

    /**
     * @see org.mmx.xdtl.runtime.command.AbstractCmdBuilder#evaluate(org.mmx.xdtl.model.Command)
     */
    @Override
    protected void evaluate(Command elem) throws Exception {
        Clear cmd = (Clear) elem;
        Context ctx = getContext();
        
        m_cmd = m_typeConv.toString(ctx.getVariableValue(CMD_NAME));
        m_target = m_typeConv.toString(m_exprEval.evaluate(ctx, cmd.getTarget()));
    }
}
