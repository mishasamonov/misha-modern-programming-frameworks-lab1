package ua.edu.sumdu.web.error;

public final class HttpException extends RuntimeException {
  private final int status;

  public HttpException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
