"use client";

import { useEffect, useState } from "react";
import { adminApi, AdminUser } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";

export default function AdminPage() {
  const { user, ready } = useAuth(true); // admin only
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    adminApi.listUsers()
      .then(setUsers)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [ready]);

  async function toggleRole(u: AdminUser) {
    setActionError(null);
    const newRole = u.role === "ADMIN" ? "USER" : "ADMIN";
    try {
      const updated = await adminApi.changeRole(u.id, newRole);
      setUsers((prev) => prev.map((x) => (x.id === u.id ? updated : x)));
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : "Failed to change role");
    }
  }

  async function deleteUser(u: AdminUser) {
    if (!confirm(`Delete ${u.email}? This cannot be undone.`)) return;
    setActionError(null);
    try {
      await adminApi.deleteUser(u.id);
      setUsers((prev) => prev.filter((x) => x.id !== u.id));
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : "Failed to delete user");
    }
  }

  if (!ready) return null;
  if (loading) return <p className="text-slate">Loading users…</p>;
  if (error) return <p className="text-red-600">{error}</p>;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Admin — Users</h1>
        <p className="text-slate text-sm mt-1">Manage accounts and roles</p>
      </div>

      {actionError && (
        <p className="text-sm text-red-600 bg-red-50 rounded-lg px-4 py-2">{actionError}</p>
      )}

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate border-b border-slate-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium">Email</th>
              <th className="text-left px-4 py-3 font-medium">Role</th>
              <th className="text-left px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                <td className="px-4 py-3 text-ink font-medium">
                  {u.email}
                  {u.id === user?.userId && (
                    <span className="ml-2 text-xs text-slate">(you)</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                    u.role === "ADMIN"
                      ? "bg-accent/10 text-accent"
                      : "bg-slate-100 text-slate"
                  }`}>
                    {u.role}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate">
                  {new Date(u.createdAt).toLocaleDateString()}
                </td>
                <td className="px-4 py-3 flex gap-2 justify-end">
                  {u.id !== user?.userId && (
                    <>
                      <button
                        onClick={() => toggleRole(u)}
                        className="px-3 py-1 rounded-lg text-xs font-medium border border-slate-200 hover:bg-slate-50 transition"
                      >
                        {u.role === "ADMIN" ? "Make user" : "Make admin"}
                      </button>
                      <button
                        onClick={() => deleteUser(u)}
                        className="px-3 py-1 rounded-lg text-xs font-medium border border-red-200 text-red-600 hover:bg-red-50 transition"
                      >
                        Delete
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-slate">{users.length} user{users.length !== 1 ? "s" : ""} total</p>
    </div>
  );
}
