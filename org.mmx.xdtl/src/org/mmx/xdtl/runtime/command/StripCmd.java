/**
 * 
 */
package org.mmx.xdtl.runtime.command;

import java.util.List;

import org.mmx.xdtl.runtime.Context;
import org.mmx.xdtl.runtime.OsProcessException;
import org.mmx.xdtl.runtime.RuntimeCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * Cleanse a text file, stripping excess data eg. with stream utility.
 * 
 * @author vsi
 */
public class StripCmd implements RuntimeCommand {
    private final Logger m_logger = LoggerFactory.getLogger(StripCmd.class);
    
    private final String  m_cmd;
    private final String  m_source;
    private final String  m_target;
    private final boolean m_overwrite;
    private final String  m_expr;

    private OsProcessRunner m_osProcessRunner;
    private OsArgListBuilder m_argListBuilder;

    public StripCmd(String cmd, String source, String target,
            boolean overwrite, String expr) {
        super();
        m_cmd = cmd;
        m_source = source;
        m_target = target;
        m_overwrite = overwrite;
        m_expr = expr;
    }

    /**
     * @see org.mmx.xdtl.runtime.RuntimeCommand#run(org.mmx.xdtl.runtime.Context)
     */
    @Override
    public void run(Context context) throws Throwable {
        m_logger.debug(String.format("strip: cmd='%s', source='%s', " +
                "target='%s', overwrite='%s', expr='%s'",
                m_cmd, m_source, m_target, Boolean.toString(m_overwrite),
                m_expr));
        
        m_argListBuilder.addVariableEscaped("source", m_source);
        m_argListBuilder.addVariableEscaped("target", m_target);
        m_argListBuilder.addVariable("overwrite", m_overwrite);
        m_argListBuilder.addVariableEscaped("expr", m_expr);

        List<String> args = m_argListBuilder.build(m_cmd, true);

        int exitValue = m_osProcessRunner.run(args).getExitCode();
        
        if (exitValue != 0) {
            throw new OsProcessException("'strip' failed with exit value " + exitValue, exitValue);
        }
    }

    public OsProcessRunner getOsProcessRunner() {
        return m_osProcessRunner;
    }

    @Inject
    public void setOsProcessRunner(OsProcessRunner osProcessRunner) {
        m_osProcessRunner = osProcessRunner;
    }

    public OsArgListBuilder getArgListBuilder() {
        return m_argListBuilder;
    }

    @Inject
    public void setArgListBuilder(OsArgListBuilder argListBuilder) {
        m_argListBuilder = argListBuilder;
    }
}
