package vn.englishconversation.common.exception;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.dto.ApiErrorResponse;

@RestControllerAdvice
public class ApiErrors {
  public static void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    new ObjectMapper()
        .writeValue(
            response.getOutputStream(),
            new ApiErrorResponse(code, message, Map.of(), UUID.randomUUID().toString()));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiErrorResponse> known(ApiException e) {
    return body(e.status, e.code, e.getMessage(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return body(400, "VALIDATION_ERROR", "Kiểm tra lại thông tin đã nhập.", fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> malformed() {
    return body(400, "INVALID_REQUEST", "Dữ liệu yêu cầu không hợp lệ.", Map.of());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiErrorResponse> uploadTooLarge() {
    return body(413, "AUDIO_TOO_LARGE", "Bản ghi tối đa 60 giây.", Map.of());
  }

  @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class,
      MissingServletRequestParameterException.class})
  ResponseEntity<ApiErrorResponse> invalidFields() {
    return body(400, "INVALID_REQUEST", "Yêu cầu thiếu trường hoặc sai định dạng.", Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> conflict() {
    return body(409, "ACCOUNT_CONFLICT", "Không thể tạo tài khoản với email này.", Map.of());
  }

  @ExceptionHandler(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiErrorResponse> invalidType() {
    return body(400, "INVALID_REQUEST", "Định danh không hợp lệ.", Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> method() {
    return body(405, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.", Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> media() {
    return body(
        415, "UNSUPPORTED_MEDIA_TYPE", "Định dạng của yêu cầu chưa được hỗ trợ.", Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception e) {
    String id = UUID.randomUUID().toString();
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class)
        .error("Request {} failed: {}", id, e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .cacheControl(CacheControl.noStore())
        .body(
            new ApiErrorResponse(
                "INTERNAL_ERROR", "Không thể xử lý yêu cầu. Vui lòng thử lại.", Map.of(), id));
  }

  private ResponseEntity<ApiErrorResponse> body(
      int status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(new ApiErrorResponse(code, message, fields, UUID.randomUUID().toString()));
  }
}
