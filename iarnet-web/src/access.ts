/**
 * @see https://umijs.org/docs/max/access#access
 * 平台未使用登录，权限按需在此扩展
 */
export default function access() {
  return {
    canAdmin: true,
  };
}
