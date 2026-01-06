// Implements 2.权限引导与存活看板
type QueryValue = string | number | boolean | null | undefined;

export interface HttpRequestOptions {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  payload?: Record<string, unknown>;
  query?: Record<string, QueryValue>;
  headers?: Record<string, string>;
  fallbackError?: string;
}

const DEFAULT_HEADERS = {
  'Content-Type': 'application/json'
};

const DEFAULT_ERROR_MESSAGE = '网络异常，请稍后重试';

export const safeUni = () => {
  try {
    if (typeof uni !== 'undefined') {
      return uni;
    }
  } catch {
    return undefined;
  }
  return undefined;
};

export const buildQueryString = (query?: Record<string, QueryValue>) => {
  if (!query) {
    return '';
  }
  const entries = Object.entries(query).filter(
    (entry): entry is [string, string | number | boolean] =>
      entry[1] !== null && entry[1] !== undefined
  );
  if (!entries.length) {
    return '';
  }
  return `?${entries
    .map(
      ([key, value]) =>
        `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`
    )
    .join('&')}`;
};

export const httpRequest = async <T>({
  url,
  method,
  payload,
  query,
  headers,
  fallbackError
}: HttpRequestOptions): Promise<T> => {
  const targetUrl = `${url}${buildQueryString(query)}`;
  const requestHeaders = { ...DEFAULT_HEADERS, ...(headers ?? {}) };
  const errorMessage = fallbackError ?? DEFAULT_ERROR_MESSAGE;
  const api = safeUni();

  if (api?.request) {
    return new Promise<T>((resolve, reject) => {
      api.request({
        url: targetUrl,
        method,
        data: method === 'GET' ? undefined : payload,
        header: requestHeaders,
        success: (res) => {
          if (res.statusCode === 204) {
            resolve({} as T);
            return;
          }
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(res.data as T);
            return;
          }
          const message =
            (res.data as Record<string, unknown>)?.message ?? errorMessage;
          reject(new Error(String(message)));
        },
        fail: (err) => {
          reject(new Error(err?.errMsg ?? errorMessage));
        }
      });
    });
  }

  const response = await fetch(targetUrl, {
    method,
    headers: requestHeaders,
    body: method === 'GET' ? undefined : JSON.stringify(payload ?? {})
  });

  if (response.status === 204) {
    return {} as T;
  }

  if (!response.ok) {
    let message = errorMessage;
    try {
      const data = await response.json();
      if (data?.message) {
        message = data.message;
      }
    } catch {
      // ignore parse errors
    }
    throw new Error(message);
  }

  return (await response.json()) as T;
};