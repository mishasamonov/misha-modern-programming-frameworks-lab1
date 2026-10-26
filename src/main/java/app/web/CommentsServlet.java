package app.web;

import app.db.CommentDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public final class CommentsServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(CommentsServlet.class);
    private final CommentDao dao = new CommentDao();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var comments = dao.list();
            response.setContentType("application/json; charset=UTF-8");
            mapper.writeValue(response.getWriter(), comments);
        } catch (SQLException error) {
            log.error("Cannot read comments", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "DB error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String author = request.getParameter("author");
        String text = request.getParameter("text");
        if (author == null || text == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "author and text are required");
            return;
        }

        author = author.trim();
        text = text.trim();
        if (author.isEmpty() || text.isEmpty() || author.length() > 64 || text.length() > 1000) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "author must be 1–64 characters and text must be 1–1000 characters");
            return;
        }

        try {
            long id = dao.add(author, text);
            log.info("New comment id={} author={} length={}", id, author, text.length());
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (SQLException error) {
            log.error("Cannot add comment", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "DB error");
        }
    }
}
