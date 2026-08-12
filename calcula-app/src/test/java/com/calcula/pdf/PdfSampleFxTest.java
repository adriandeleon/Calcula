package com.calcula.pdf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import com.calcula.ui.FxTestSupport;
import com.calcula.ui.SheetRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Writes a real sheet to a real path, so a human (or a PDF reader) can open it.
 *
 * <p>Off by default — this is a development aid, not an assertion. Run it with
 * {@code -Dcalcula.pdf.sample=/tmp/sheet.pdf}.
 */
@Tag("fx")
@EnabledIfSystemProperty(named = "calcula.pdf.sample", matches = ".+")
class PdfSampleFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @Test
    void writeASampleSheet() throws Exception {
        Path target = Path.of(System.getProperty("calcula.pdf.sample"));
        List<Expr> stack =
                List.of("(x+1)/(x-1)", "sqrt(2)", "sin(x)^2 + cos(x)^2", "[[1,2],[3,4]]", "1/3 + 1/6").stream()
                        .map(Parser::parse)
                        .toList();
        PdfWriter.writeImage(
                target, FxTestSupport.callOnFx(() -> SheetRenderer.render(stack, 22)), SheetRenderer.SCALE);
        System.out.println("wrote " + Files.size(target) + " bytes to " + target);
    }
}
