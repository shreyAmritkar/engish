const USER_KEY = "style_communicator_user_id";

export function getUserId(): string {
  if (typeof window === "undefined") {
    return "";
  }
  let id = localStorage.getItem(USER_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(USER_KEY, id);
  }
  return id;
}
