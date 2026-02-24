import http from "k6/http";

const BASE_URL = "https://api.goormpopcorn.shop";
const POPUP_ID = "07c79042-f179-452e-9318-0d3abb403c44";

export default function() {
  // 로그인 먼저
  const loginBody = JSON.stringify({
    email: "popcorn1@popcorn.com",
    password: "test123"
  });

  const loginRes = http.post(`${BASE_URL}/api/users/v1/auth/login`, loginBody, {
    headers: { "Content-Type": "application/json" },
    timeout: "30s"
  });

  console.log("=== 로그인 응답 ===");
  console.log(`Status: ${loginRes.status}`);
  console.log(`Body: ${loginRes.body}`);

  if (loginRes.status >= 200 && loginRes.status < 300) {
    const loginData = JSON.parse(loginRes.body);
    const token = loginData.token;

    // 주문 생성 테스트
    const orderBody = JSON.stringify({
      orderType: "GOODS",
      popupId: POPUP_ID,
      paymentMethod: "CARD",
      items: [{
        orderItemType: "GOODS",
        qty: 1,
        unitPrice: 15000,
        goodsId: "sample-goods-id"
      }]
    });

    const orderRes = http.post(`${BASE_URL}/api/orders/v1/`, orderBody, {
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      timeout: "30s"
    });

    console.log("\n=== 주문 생성 응답 ===");
    console.log(`Status: ${orderRes.status}`);
    console.log(`Body: ${orderRes.body}`);

    // JSON 파싱 시도
    try {
      const orderData = JSON.parse(orderRes.body);
      console.log("\n=== 파싱된 주문 데이터 ===");
      console.log("Full data:", JSON.stringify(orderData, null, 2));
      console.log("data.id:", orderData?.data?.id);
      console.log("data.orderId:", orderData?.data?.orderId);
      console.log("orderId:", orderData?.orderId);
      console.log("id:", orderData?.id);

      // 모든 가능한 경로 체크
      console.log("\n=== 모든 가능한 orderId 경로 ===");
      console.log("orderData:", orderData);
      console.log("orderData.data:", orderData?.data);
      if (orderData?.data) {
        Object.keys(orderData.data).forEach(key => {
          console.log(`data.${key}:`, orderData.data[key]);
        });
      }

    } catch (e) {
      console.log("JSON 파싱 실패:", e);
    }
  } else {
    console.log("로그인 실패");
  }
}